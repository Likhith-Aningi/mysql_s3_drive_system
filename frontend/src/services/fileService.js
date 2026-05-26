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
  async uploadToS3(uploadUrl, file, onUploadProgress) {
    await axios.put(uploadUrl, file, {
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
      onUploadProgress,
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

  async confirmUpload(fileMetadataId) {
    await api.post(`/files/${fileMetadataId}/confirm`)
  },

  async deleteFile(fileId) {
    await api.delete(`/files/${fileId}`)
  },

  async initiateMultipartUpload(fileName, contentType, fileSize) {
    const res = await api.post('/files/upload/multipart/initiate', { fileName, contentType, fileSize })
    return res.data // { fileMetadataId, uploadId, s3Key, partSize }
  },

  async getPartUrl(fileMetadataId, partNumber) {
    const res = await api.get(`/files/${fileMetadataId}/part-url`, { params: { partNumber } })
    return res.data.uploadUrl
  },

  // Returns eTag from S3 response header — requires S3 CORS ExposeHeaders: ["ETag"]
  async uploadPart(uploadUrl, chunk, onUploadProgress) {
    const res = await axios.put(uploadUrl, chunk, {
      headers: { 'Content-Type': 'application/octet-stream' },
      onUploadProgress,
    })
    const eTag = res.headers['etag']
    if (!eTag) {
      throw new Error(
        'S3 did not return an ETag for this part. Add "ETag" to ExposeHeaders in your S3 bucket CORS configuration.'
      )
    }
    return eTag
  },

  async completeMultipartUpload(fileMetadataId, uploadId, parts) {
    await api.post(`/files/${fileMetadataId}/complete-multipart`, { uploadId, parts })
  },
}
