import { useState, useEffect, useCallback } from 'react'
import { fileService } from '../services/fileService'

export function useFiles() {
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchFiles = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await fileService.listFiles()
      setFiles(data)
    } catch (err) {
      setError(err.response?.data?.detail || 'Failed to load files')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchFiles() }, [fetchFiles])

  const removeFile = useCallback((id) => {
    setFiles((prev) => prev.filter((f) => f.id !== id))
  }, [])

  return { files, loading, error, fetchFiles, removeFile }
}
