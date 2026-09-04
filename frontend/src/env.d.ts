/// <reference types="vite/client" />

interface Window {
  desktop?: {
    chooseBackupPath(): string
    chooseRestorePath(): string
  }
}
