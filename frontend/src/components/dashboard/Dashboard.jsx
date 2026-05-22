import { useState } from 'react'
import { Upload, RefreshCw, FolderOpen } from 'lucide-react'
import Navbar from '../shared/Navbar'
import FileCard from './FileCard'
import UploadModal from './UploadModal'
import ShareModal from './ShareModal'
import PreviewModal from './PreviewModal'
import { useFiles } from '../../hooks/useFiles'
import { fileService } from '../../services/fileService'

export default function Dashboard() {
  const { files, loading, error, fetchFiles, removeFile } = useFiles()
  const [uploadOpen, setUploadOpen] = useState(false)
  const [shareFile, setShareFile] = useState(null)
  const [previewFile, setPreviewFile] = useState(null)
  const [actionError, setActionError] = useState('')

  const handleDownload = async (file) => {
    if (file.cloudfrontUrl) {
      window.open(file.cloudfrontUrl, '_blank')
      return
    }
    try {
      const url = await fileService.getDownloadUrl(file.id)
      window.open(url, '_blank')
    } catch {
      setActionError('Failed to get download link')
    }
  }

  const handleDelete = async (file) => {
    if (!window.confirm(`Delete "${file.originalName}"?`)) return
    try {
      await fileService.deleteFile(file.id)
      removeFile(file.id)
    } catch {
      setActionError('Failed to delete file')
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100">My Drive</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {files.length} file{files.length !== 1 ? 's' : ''}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={fetchFiles}
              className="p-2 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-200 dark:hover:bg-gray-800 rounded-lg transition-colors"
              title="Refresh"
            >
              <RefreshCw className="w-5 h-5" />
            </button>
            <button
              onClick={() => setUploadOpen(true)}
              className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium shadow-sm"
            >
              <Upload className="w-4 h-4" />
              Upload
            </button>
          </div>
        </div>

        {actionError && (
          <div className="mb-4 px-4 py-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 rounded-lg text-sm flex justify-between items-center">
            {actionError}
            <button onClick={() => setActionError('')} className="text-red-400 hover:text-red-600 ml-2 text-lg leading-none">
              ×
            </button>
          </div>
        )}

        {loading && (
          <div className="flex justify-center items-center py-24">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
          </div>
        )}

        {error && (
          <div className="text-center py-16 text-red-500 dark:text-red-400">{error}</div>
        )}

        {!loading && !error && files.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24 text-gray-400 dark:text-gray-500">
            <FolderOpen className="w-16 h-16 mb-4 text-gray-300 dark:text-gray-600" />
            <p className="text-lg font-medium text-gray-500 dark:text-gray-400">No files yet</p>
            <p className="text-sm mt-1">Upload your first file to get started</p>
            <button
              onClick={() => setUploadOpen(true)}
              className="mt-4 flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
            >
              <Upload className="w-4 h-4" />
              Upload file
            </button>
          </div>
        )}

        {!loading && !error && files.length > 0 && (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {files.map((file) => (
              <FileCard
                key={file.id}
                file={file}
                onDownload={handleDownload}
                onDelete={handleDelete}
                onShare={setShareFile}
                onPreview={setPreviewFile}
              />
            ))}
          </div>
        )}
      </main>

      {uploadOpen && (
        <UploadModal
          onClose={() => setUploadOpen(false)}
          onSuccess={() => { setUploadOpen(false); fetchFiles() }}
        />
      )}

      {shareFile && (
        <ShareModal
          file={shareFile}
          onClose={() => setShareFile(null)}
        />
      )}

      {previewFile && (
        <PreviewModal
          file={previewFile}
          onClose={() => setPreviewFile(null)}
        />
      )}
    </div>
  )
}
