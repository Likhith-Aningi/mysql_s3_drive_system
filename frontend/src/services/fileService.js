import api from './api'
import axios from 'axios'

export const fileService = {
  async listFiles() {
    const res = await api.get('/files')
    return res.data
  },

  async initiateUpload(fileName, contentType, fileSize) {
    const res = await api.post('/files/upload/initiate', { fileName, contentType, fileSize })
    return res.data
  },

  // File goes directly to S3 — never touches our backend
  async uploadToS3(uploadUrl, file) {
    await axios.put(uploadUrl, file, {
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
    })
  },

  async getDownloadUrl(fileId) {
    const res = await api.get(`/files/${fileId}/download`)
    return res.data.url
  },

  async getShareUrl(fileId) {
    const res = await api.get(`/files/${fileId}/share`)
    return res.data.url
  },

  async deleteFile(fileId) {
    await api.delete(`/files/${fileId}`)
  },
}
