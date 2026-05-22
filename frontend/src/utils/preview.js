const TEXT_EXTENSIONS = new Set([
  '.txt', '.js', '.jsx', '.ts', '.tsx', '.java', '.py', '.css',
  '.html', '.xml', '.json', '.yaml', '.yml', '.sh', '.md',
  '.go', '.rs', '.c', '.cpp', '.h', '.php', '.rb', '.kt', '.swift',
])

const TEXT_CONTENT_TYPES = [
  'text/',
  'application/json',
  'application/javascript',
  'application/xml',
  'application/x-sh',
]

// Returns 'image' | 'video' | 'text' | null
export function getPreviewType(file) {
  const ct = file.contentType || ''
  if (ct.startsWith('image/')) return 'image'
  if (ct.startsWith('video/')) return 'video'
  if (TEXT_CONTENT_TYPES.some(t => ct.startsWith(t))) return 'text'
  const ext = file.originalName?.toLowerCase().match(/(\.[^.]+)$/)?.[1]
  if (ext && TEXT_EXTENSIONS.has(ext)) return 'text'
  return null
}
