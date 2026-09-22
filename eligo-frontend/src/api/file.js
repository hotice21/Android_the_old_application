import { download, upload } from "./request"

export function uploadImage(filePath) {
  return upload({
    path: "/api/v1/files/images",
    filePath,
    name: "file",
  })
}

export function downloadOwnedFile(path) {
  return download({ path })
}
