import { useState, useRef } from 'react'
import { Upload, X, CheckCircle, AlertCircle, CloudUpload } from 'lucide-react'
import { fileService } from '../../services/fileService'

export default function UploadModal({ onClose, onSuccess }) {
  const [dragOver, setDragOver] = useState(false)
  const [files, setFiles] = useState([])
  const [uploading, setUploading] = useState(false)
  const [results, setResults] = useState([])
  const inputRef = useRef()

  const addFiles = (selected) => {
    setFiles((prev) => [...prev, ...Array.from(selected)])
  }

  const removeFile = (index) => {
    setFiles((prev) => prev.filter((_, i) => i !== index))
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

  const handleUpload = async () => {
    if (!files.length) return
    setUploading(true)
    const newResults = []

    for (const file of files) {
      let fileMetadataId = null
      try {
        const { uploadUrl, fileMetadataId: id } = await fileService.initiateUpload(
          file.name,
          file.type || 'application/octet-stream',
          file.size
        )
        fileMetadataId = id

        await fileService.uploadToS3(uploadUrl, file)

        try {
          await fileService.confirmUpload(fileMetadataId)
        } catch {
          newResults.push({ name: file.name, success: false, reason: 'Upload may not have completed — it will be cleaned up automatically' })
          continue
        }

        newResults.push({ name: file.name, success: true })
      } catch (err) {
        newResults.push({ name: file.name, success: false, reason: classifyUploadError(err) })
      }
    }

    setResults(newResults)
    setUploading(false)
    if (newResults.every((r) => r.success)) {
      setTimeout(onSuccess, 800)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl w-full max-w-md">
        <div className="flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">Upload files</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {results.length === 0 ? (
            <>
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

        {results.length === 0 && (
          <div className="px-5 pb-5 flex gap-2">
            <button
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 text-sm font-medium transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handleUpload}
              disabled={!files.length || uploading}
              className="flex-1 flex items-center justify-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium transition-colors"
            >
              {uploading ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" />
                  Uploading...
                </>
              ) : (
                <>
                  <Upload className="w-4 h-4" />
                  Upload{files.length > 0 ? ` (${files.length})` : ''}
                </>
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
