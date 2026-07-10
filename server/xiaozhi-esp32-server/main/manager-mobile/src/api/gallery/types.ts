export interface GalleryImage {
  id: string
  accountId: string
  fileId: string
  filename: string
  mimeType: string
  sizeBytes: number
  thumbUrl?: string
  thumbPath?: string
  tags: string[]
  status: string
  createdAt?: string
}

export interface GalleryListResponse {
  images: GalleryImage[]
  count: number
}

export interface GalleryDownloadResponse {
  url: string
}
