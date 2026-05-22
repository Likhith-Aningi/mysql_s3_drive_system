import { Download, Trash2, Share2, FileText, Image, Film, Music, Archive, File } from 'lucide-react'
import { getPreviewType } from '../../utils/preview'

const MIME_ICONS = [
  ['image/', Image],
  ['video/', Film],
  ['audio/', Music],
  ['application/pdf', FileText],
  ['application/zip', Archive],
  ['application/x-zip', Archive],
]

function getIcon(contentType) {
  for (const [prefix, Icon] of MIME_ICONS) {
    if (contentType?.startsWith(prefix) || contentType === prefix) return Icon
  }
  return File
}

function formatBytes(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 ** 3) return (bytes / 1024 ** 2).toFixed(1) + ' MB'
  return (bytes / 1024 ** 3).toFixed(1) + ' GB'
}

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  })
}

export default function FileCard({ file, onDownload, onDelete, onShare, onPreview }) {
  const Icon = getIcon(file.contentType)
  const previewType = getPreviewType(file)

  const iconArea = (
    <div className="w-12 h-12 rounded-xl bg-blue-50 dark:bg-blue-900/30 flex items-center justify-center mb-3 group-hover:bg-blue-100 dark:group-hover:bg-blue-900/50 transition-colors">
      <Icon className="w-6 h-6 text-blue-500 dark:text-blue-400" />
    </div>
  )

  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4 hover:shadow-md dark:hover:shadow-gray-900 transition-shadow group">
      <div className="flex flex-col items-center">
        {previewType ? (
          <button
            onClick={() => onPreview(file)}
            className="rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-300"
            title={`Preview ${file.originalName}`}
          >
            {iconArea}
          </button>
        ) : (
          iconArea
        )}
        <p
          className="text-sm font-medium text-gray-800 dark:text-gray-100 text-center w-full truncate"
          title={file.originalName}
        >
          {file.originalName}
        </p>
        <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">{formatBytes(file.fileSize)}</p>
        <p className="text-xs text-gray-400 dark:text-gray-500">{formatDate(file.uploadedAt)}</p>
      </div>

      <div className="flex items-center justify-center gap-1 mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
        <button
          onClick={() => onDownload(file)}
          className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
          title="Download"
        >
          <Download className="w-4 h-4" />
        </button>
        <button
          onClick={() => onShare(file)}
          className="p-1.5 text-gray-400 hover:text-green-600 hover:bg-green-50 rounded-lg transition-colors"
          title="Share"
        >
          <Share2 className="w-4 h-4" />
        </button>
        <button
          onClick={() => onDelete(file)}
          className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
          title="Delete"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
    </div>
  )
}
