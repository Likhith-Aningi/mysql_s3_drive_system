import { useState, useRef, useEffect } from 'react'
import { Upload, X, CheckCircle, AlertCircle, CloudUpload, Pause, Play } from 'lucide-react'
import { fileService } from '../../services/fileService'

const FINGERPRINT = (f) => `drive-upload-${f.name}-${f.size}-${f.lastModified}`

function loadSession(file) {
  try { return JSON.parse(localStorage.getItem(FINGERPRINT(file)) || 'null') }
  catch { return null }
}

function saveSession(file, data) {
  localStorage.setItem(FINGERPRINT(file), JSON.stringify(data))
}

function clearSession(file) {
  localStorage.removeItem(FINGERPRINT(file))
}

export default function UploadModal({ onClose, onSuccess }) {
  const [dragOver, setDragOver] = useState(false)
  const [files, setFiles] = useState([])
  const [resumable, setResumable] = useState([]) // indices of files with saved sessions
  const [uploading, setUploading] = useState(false)
  const [paused, setPaused] = useState(false)
  const [progress, setProgress] = useState([])
  const [results, setResults] = useState([])
  const inputRef = useRef()
  const pausedRef = useRef(false)

  const updateProgress = (index, patch) =>
    setProgress((prev) => prev.map((p, i) => (i === index ? { ...p, ...patch } : p)))

  const detectResumable = (fileList) => {
    const indices = fileList
      .map((f, i) => (loadSession(f) ? i : -1))
      .filter((i) => i >= 0)
    setResumable(indices)
  }

  const addFiles = (selected) => {
    const arr = Array.from(selected)
    setFiles((prev) => {
      const next = [...prev, ...arr]
      detectResumable(next)
      return next
    })
  }

  const removeFile = (index) => {
    setFiles((prev) => {
      const next = prev.filter((_, i) => i !== index)
      detectResumable(next)
      return next
    })
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragOver(false)
    addFiles(e.dataTransfer.files)
  }

  const classifyUploadError = (err) => {
    if (!err?.response) return 'Network error — check your connection and try again'
    const status = err.response.status
    if (status === 403) return 'Upload link expired — please try again'
    if (status === 413) return 'File is too large'
    if (status === 400) return 'Invalid request — check file name and type'
    return `Upload failed (error ${status})`
  }

  const uploadFileMultipart = async (file, idx) => {
    let session = loadSession(file)
    let { fileMetadataId, uploadId, partSize, totalParts, completedParts } = session ?? {}

    if (!session) {
      const init = await fileService.initiateMultipartUpload(
        file.name,
        file.type || 'application/octet-stream',
        file.size
      )
      fileMetadataId = init.fileMetadataId
      uploadId = init.uploadId
      partSize = init.partSize
      totalParts = Math.ceil(file.size / partSize)
      completedParts = []
      saveSession(file, { fileMetadataId, uploadId, s3Key: init.s3Key, partSize, totalParts, completedParts })
    }

    const doneSet = new Set(completedParts.map((p) => p.partNumber))

    for (let partNumber = 1; partNumber <= totalParts; partNumber++) {
      if (doneSet.has(partNumber)) continue

      if (pausedRef.current) return 'paused'

      const start = (partNumber - 1) * partSize
      const chunk = file.slice(start, start + partSize)

      const uploadUrl = await fileService.getPartUrl(fileMetadataId, partNumber)
      const eTag = await fileService.uploadPart(uploadUrl, chunk, (e) => {
        const partsDone = completedParts.length
        const partProgress = e.total > 0 ? e.loaded / e.total : 0
        const overall = ((partsDone + partProgress) / totalParts) * 100
        updateProgress(idx, { loaded: Math.round(overall), total: 100 })
      })

      completedParts = [...completedParts, { partNumber, eTag }]
      saveSession(file, { fileMetadataId, uploadId, partSize, totalParts, completedParts })
    }

    updateProgress(idx, { loaded: 100, total: 100, phase: 'confirming' })
    await fileService.completeMultipartUpload(fileMetadataId, uploadId, completedParts)
    clearSession(file)
    return 'done'
  }

  const handleUpload = async () => {
    if (!files.length) return
    pausedRef.current = false
    setPaused(false)
    setUploading(true)
    setProgress(files.map(() => ({ loaded: 0, total: 100, phase: 'waiting' })))
    const newResults = []

    for (let idx = 0; idx < files.length; idx++) {
      const file = files[idx]

      // Skip files that already finished in this session
      if (newResults[idx]?.success) continue

      updateProgress(idx, { phase: 'uploading', loaded: 0, total: 100 })

      try {
        const outcome = await uploadFileMultipart(file, idx)

        if (outcome === 'paused') {
          // Mark remaining files as waiting; stop loop
          for (let j = idx; j < files.length; j++) {
            updateProgress(j, { phase: 'waiting' })
          }
          break
        }

        updateProgress(idx, { phase: 'done' })
        newResults[idx] = { name: file.name, success: true }
      } catch (err) {
        updateProgress(idx, { phase: 'error' })
        newResults[idx] = { name: file.name, success: false, reason: classifyUploadError(err) }
      }
    }

    setUploading(false)

    const allDone = newResults.length === files.length && newResults.every((r) => r?.success)
    if (allDone) {
      setResults(newResults)
      setTimeout(onSuccess, 800)
    } else if (!pausedRef.current) {
      // At least one error; show results
      const filled = files.map((f, i) => newResults[i] ?? { name: f.name, success: false, reason: 'Not attempted' })
      setResults(filled)
    }
    // If paused, keep uploading=false and paused=true; user can resume
  }

  const handlePause = () => {
    pausedRef.current = true
    setPaused(true)
  }

  const handleResume = () => {
    handleUpload()
  }

  // Re-detect resumable when files change
  useEffect(() => {
    detectResumable(files)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const resumableCount = resumable.length

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl w-full max-w-md">
        <div className="flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">Upload files</h2>
          <button
            onClick={onClose}
            disabled={uploading}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {uploading || paused ? (
            <>
              {paused && (
                <p className="text-xs text-yellow-600 dark:text-yellow-400 font-medium">
                  Upload paused — progress is saved. Click Resume to continue.
                </p>
              )}
              <ul className="space-y-3">
                {files.map((f, i) => {
                  const p = progress[i] ?? { loaded: 0, total: 100, phase: 'waiting' }
                  const pct = p.total > 0 ? Math.min(100, Math.round((p.loaded / p.total) * 100)) : 0
                  const done = p.phase === 'done'
                  const error = p.phase === 'error'
                  const confirming = p.phase === 'confirming'
                  const waiting = p.phase === 'waiting'
                  return (
                    <li key={i} className="space-y-1.5">
                      <div className="flex items-center justify-between gap-2 text-sm">
                        <span className="truncate text-gray-700 dark:text-gray-200 flex-1">{f.name}</span>
                        <span className={`flex-shrink-0 text-xs font-medium tabular-nums ${
                          done ? 'text-green-500' : error ? 'text-red-500' : waiting ? 'text-gray-400' : 'text-gray-400 dark:text-gray-500'
                        }`}>
                          {done ? 'Done' : error ? 'Failed' : confirming ? 'Saving…' : waiting ? 'Waiting' : `${pct}%`}
                        </span>
                      </div>
                      <div className="h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-150 ${
                            done ? 'bg-green-500' : error ? 'bg-red-500' : waiting ? 'bg-gray-300' : 'bg-blue-500'
                          }`}
                          style={{ width: `${done || confirming ? 100 : pct}%` }}
                        />
                      </div>
                    </li>
                  )
                })}
              </ul>
            </>
          ) : results.length === 0 ? (
            <>
              {resumableCount > 0 && (
                <div className="text-xs text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 rounded-lg px-3 py-2">
                  {resumableCount === 1
                    ? '1 file has a previous upload in progress — it will resume from where it left off.'
                    : `${resumableCount} files have previous uploads in progress — they will resume automatically.`}
                </div>
              )}
              <div
                onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
                onDragLeave={() => setDragOver(false)}
                onDrop={handleDrop}
                onClick={() => inputRef.current?.click()}
                className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors ${
                  dragOver
                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                    : 'border-gray-300 dark:border-gray-600 hover:border-blue-400 dark:hover:border-blue-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'
                }`}
              >
                <CloudUpload className="w-10 h-10 text-gray-400 dark:text-gray-500 mx-auto mb-2" />
                <p className="text-sm text-gray-600 dark:text-gray-300 font-medium">Drag & drop files here</p>
                <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">or click to browse</p>
                <input
                  ref={inputRef}
                  type="file"
                  multiple
                  className="hidden"
                  onChange={(e) => addFiles(e.target.files)}
                />
              </div>

              {files.length > 0 && (
                <ul className="space-y-2 max-h-48 overflow-y-auto">
                  {files.map((f, i) => (
                    <li
                      key={i}
                      className="flex items-center justify-between text-sm bg-gray-50 dark:bg-gray-700 rounded-lg px-3 py-2"
                    >
                      <span className="truncate text-gray-700 dark:text-gray-200 flex-1 mr-2">{f.name}</span>
                      {resumable.includes(i) && (
                        <span className="text-xs text-blue-500 mr-2 flex-shrink-0">resuming</span>
                      )}
                      <button
                        onClick={() => removeFile(i)}
                        className="text-gray-400 hover:text-red-500 flex-shrink-0"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : (
            <ul className="space-y-2">
              {results.map((r, i) => (
                <li key={i} className="flex items-start gap-2 text-sm">
                  {r.success ? (
                    <CheckCircle className="w-4 h-4 text-green-500 flex-shrink-0 mt-0.5" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
                  )}
                  <div>
                    <span className={r.success ? 'text-gray-700 dark:text-gray-200' : 'text-red-600 dark:text-red-400'}>{r.name}</span>
                    {!r.success && r.reason && (
                      <p className="text-xs text-red-400 dark:text-red-500 mt-0.5">{r.reason}</p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {uploading && (
          <div className="px-5 pb-5">
            <button
              onClick={handlePause}
              className="w-full flex items-center justify-center gap-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 text-sm font-medium transition-colors"
            >
              <Pause className="w-4 h-4" />
              Pause
            </button>
          </div>
        )}

        {paused && (
          <div className="px-5 pb-5 flex gap-2">
            <button
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 text-sm font-medium transition-colors"
            >
              Close
            </button>
            <button
              onClick={handleResume}
              className="flex-1 flex items-center justify-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg hover:bg-blue-700 text-sm font-medium transition-colors"
            >
              <Play className="w-4 h-4" />
              Resume
            </button>
          </div>
        )}

        {!uploading && !paused && results.length === 0 && (
          <div className="px-5 pb-5 flex gap-2">
            <button
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 text-sm font-medium transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handleUpload}
              disabled={!files.length}
              className="flex-1 flex items-center justify-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium transition-colors"
            >
              <Upload className="w-4 h-4" />
              Upload{files.length > 0 ? ` (${files.length})` : ''}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
