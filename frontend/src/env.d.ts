/// <reference types="vite/client" />

declare const __APP_VERSION__: string

interface Window {
  desktop?: {
    chooseBackupPath(): string
    chooseRestorePath(): string
    exitApplication(): void
  }
}
