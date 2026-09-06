/// <reference types="vite/client" />

declare const __APP_VERSION__: string

interface Window {
  desktop?: {
    chooseBackupPath(): string
    chooseRestorePath(): string
    saveDownload(fileName: string, base64Data: string): string
    exitApplication(): void
  }
}
