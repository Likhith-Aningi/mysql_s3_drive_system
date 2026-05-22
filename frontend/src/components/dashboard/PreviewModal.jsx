import { useState, useEffect } from 'react'
import { X, Loader2, AlertCircle } from 'lucide-react'
import { fileService } from '../../services/fileService'
import { getPreviewType } from '../../utils/preview'

const MAX_TEXT_BYTES = 200_000

export default function PreviewModal({ file, onClose }) {
  const previewType = getPreviewType(file)
  const [url, setUrl] = useState('')
  const [textContent, setTextContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const fileUrl = file.cloudfrontUrl || await fileService.getDownloadUrl(file.id)
        if (cancelled) return
        setUrl(fileUrl)

        if (previewType === 'text') {
          const res = await fetch(fileUrl)
          if (!res.ok) throw new Error(`HTTP ${res.status}`)
          const text = await res.text()
          if (cancelled) return
          setTextContent(
            text.length > MAX_TEXT_BYTES
              ? text.slice(0, MAX_TEXT_BYTES) + '\n\n… [file truncated for preview]'
              : text
          )
        }
      } catch {
        if (!cancelled) setError('Failed to load preview')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div
      className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center z-50 p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between p-4 border-b border-gray-100 dark:border-gray-700 flex-shrink-0">
          <p className="text-sm font-medium text-gray-700 dark:text-gray-200 truncate pr-4">{file.originalName}</p>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 flex-shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-auto flex items-center justify-center p-4 min-h-0">
          {loading && <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />}

          {error && (
            <div className="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
              <AlertCircle className="w-10 h-10" />
              <p className="text-sm">{error}</p>
            </div>
          )}

          {!loading && !error && previewType === 'image' && (
            <img
              src={url}
              alt={file.originalName}
              className="max-w-full max-h-full object-contain rounded-lg"
            />
          )}

          {!loading && !error && previewType === 'video' && (
            <video
              src={url}
              controls
              className="max-w-full max-h-full rounded-lg"
            />
          )}

          {!loading && !error && previewType === 'text' && (
            <pre className="w-full h-full overflow-auto text-xs text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-900 p-4 rounded-lg font-mono leading-relaxed whitespace-pre-wrap break-all">
              {textContent}
            </pre>
          )}
        </div>
      </div>
    </div>
  )
}
