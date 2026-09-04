import { httpGateway } from './httpGateway'
import { mockGateway } from './mockGateway'

export const gateway = import.meta.env.VITE_USE_MOCK === 'true' ? mockGateway : httpGateway

