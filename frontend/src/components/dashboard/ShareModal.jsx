import { useState } from 'react'
import { X, Link, Copy, CheckCheck, Loader2, Infinity, Clock } from 'lucide-react'
import { fileService } from '../../services/fileService'

export default function ShareModal({ file, onClose }) {
  const hasCloudfrontUrl = Boolean(file.cloudfrontUrl)
  const [linkType, setLinkType] = useState('expiring')
  const [url, setUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState('')

  const handleLinkTypeChange = (type) => {
    setLinkType(type)
    setUrl('')
    setCopied(false)
    setError('')
  }

  const generateLink = async () => {
    if (linkType === 'permanent') {
      // CloudFront URL is already in the file object — no API call needed
      setUrl(file.cloudfrontUrl)
      return
    }
    setLoading(true)
    setError('')
    try {
      const shareUrl = await fileService.getShareUrl(file.id)
      setUrl(shareUrl)
    } catch {
      setError('Failed to generate share link')
    } finally {
      setLoading(false)
    }
  }

  const copyToClipboard = async () => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setError('Failed to copy to clipboard')
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl w-full max-w-md">
        <div className="flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">Share file</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <div className="flex items-center gap-3 bg-gray-50 dark:bg-gray-700/50 rounded-xl p-3">
            <div className="bg-blue-100 dark:bg-blue-900/50 rounded-lg p-2 flex-shrink-0">
              <Link className="w-5 h-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">{file.originalName}</p>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {linkType === 'permanent' ? 'No expiry — served via CDN' : 'Expires in 7 days'}
              </p>
            </div>
          </div>

          {hasCloudfrontUrl && (
            <div className="flex rounded-lg border border-gray-200 dark:border-gray-600 p-1 bg-gray-50 dark:bg-gray-700">
              <button
                onClick={() => handleLinkTypeChange('expiring')}
                className={`flex-1 flex items-center justify-center gap-1.5 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
                  linkType === 'expiring'
                    ? 'bg-white dark:bg-gray-600 text-blue-600 dark:text-blue-400 shadow-sm'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
                }`}
              >
                <Clock className="w-3.5 h-3.5" />
                7-day link
              </button>
              <button
                onClick={() => handleLinkTypeChange('permanent')}
                className={`flex-1 flex items-center justify-center gap-1.5 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
                  linkType === 'permanent'
                    ? 'bg-white dark:bg-gray-600 text-blue-600 dark:text-blue-400 shadow-sm'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
                }`}
              >
                <Infinity className="w-3.5 h-3.5" />
                Permanent link
              </button>
            </div>
          )}

          {error && (
            <div className="px-4 py-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 rounded-lg text-sm">
              {error}
            </div>
          )}

          {!url ? (
            <button
              onClick={generateLink}
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 bg-blue-600 text-white px-4 py-2.5 rounded-lg hover:bg-blue-700 disabled:opacity-50 text-sm font-medium transition-colors"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Generating...
                </>
              ) : (
                <>
                  <Link className="w-4 h-4" />
                  Generate {linkType === 'permanent' ? 'permanent' : '7-day'} link
                </>
              )}
            </button>
          ) : (
            <div className="space-y-2">
              <div className="flex items-center gap-2 bg-gray-50 dark:bg-gray-700 rounded-lg p-3 border border-gray-200 dark:border-gray-600">
                <input
                  type="text"
                  readOnly
                  value={url}
                  className="flex-1 bg-transparent text-xs text-gray-600 dark:text-gray-300 outline-none truncate"
                />
              </div>
              <button
                onClick={copyToClipboard}
                className={`w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  copied
                    ? 'bg-green-600 text-white'
                    : 'bg-blue-600 text-white hover:bg-blue-700'
                }`}
              >
                {copied ? (
                  <>
                    <CheckCheck className="w-4 h-4" />
                    Copied!
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4" />
                    Copy link
                  </>
                )}
              </button>
            </div>
          )}
        </div>

        <div className="px-5 pb-5">
          <button
            onClick={onClose}
            className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 text-sm font-medium transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
