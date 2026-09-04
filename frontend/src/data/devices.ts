import type { Device } from '../domain/types'

const iphoneGenerations = ['6', '6 Plus', '6s', '6s Plus', 'SE', '7', '7 Plus', '8', '8 Plus', 'X', 'XR', 'XS', 'XS Max', '11', '11 Pro', '11 Pro Max', 'SE (2. gen)', '12', '12 mini', '12 Pro', '12 Pro Max', '13', '13 mini', '13 Pro', '13 Pro Max', 'SE (3. gen)', '14', '14 Plus', '14 Pro', '14 Pro Max', '15', '15 Plus', '15 Pro', '15 Pro Max', '16', '16 Plus', '16 Pro', '16 Pro Max', '16e', '17', '17 Air', '17 Pro', '17 Pro Max']
const samsungS = ['S9', 'S9+', 'S10', 'S10+', 'S10e', 'S20', 'S20+', 'S20 Ultra', 'S20 FE', 'S21', 'S21+', 'S21 Ultra', 'S21 FE', 'S22', 'S22+', 'S22 Ultra', 'S23', 'S23+', 'S23 Ultra', 'S23 FE', 'S24', 'S24+', 'S24 Ultra', 'S24 FE', 'S25', 'S25+', 'S25 Ultra', 'S25 Edge', 'S25 FE']
const samsungA = ['A10', 'A12', 'A13', 'A14', 'A15', 'A16', 'A20e', 'A21s', 'A22', 'A23', 'A25 5G', 'A26 5G', 'A32', 'A33 5G', 'A34 5G', 'A35 5G', 'A36 5G', 'A40', 'A41', 'A42 5G', 'A50', 'A51', 'A52', 'A52s', 'A53 5G', 'A54 5G', 'A55 5G', 'A56 5G', 'A70', 'A71', 'A72', 'A80']
const samsungZ = ['Z Flip', 'Z Flip3', 'Z Flip4', 'Z Flip5', 'Z Flip6', 'Z Flip7', 'Z Fold2', 'Z Fold3', 'Z Fold4', 'Z Fold5', 'Z Fold6', 'Z Fold7']
const xcover = ['XCover 4s', 'XCover Pro', 'XCover 5', 'XCover 6 Pro', 'XCover 7', 'XCover 7 Pro']
const pixels = ['5', '5a', '6', '6 Pro', '6a', '7', '7 Pro', '7a', '8', '8 Pro', '8a', '9', '9 Pro', '9 Pro XL', '9a', '10', '10 Pro', '10 Pro XL']

const apple = iphoneGenerations.map<Device>(model => ({ manufacturer: 'Apple', family: 'iPhone', model: `iPhone ${model}`, aliases: ['ip', 'iphone', `ip${model.replace(/\s/g, '')}`], operatingSystem: 'IOS', type: 'PHONE' }))
const samsung = [...samsungS.map(model => ['Galaxy S', model]), ...samsungA.map(model => ['Galaxy A', model]), ...samsungZ.map(model => ['Galaxy Z', model]), ...xcover.map(model => ['Galaxy XCover', model])]
  .map<Device>(([family, model]) => ({ manufacturer: 'Samsung', family, model: `Samsung Galaxy ${model}`, aliases: ['sm', 'samsung', model.toLowerCase().replace(/\s/g, '')], operatingSystem: 'ANDROID', type: 'PHONE' }))
const google = pixels.map<Device>(model => ({ manufacturer: 'Google', family: 'Pixel', model: `Google Pixel ${model}`, aliases: ['pixel', `pixel${model.replace(/\s/g, '')}`], operatingSystem: 'ANDROID', type: 'PHONE' }))

export const devices: Device[] = [
  ...apple,
  ...samsung,
  ...google,
  { manufacturer: 'Doro', family: 'General', model: 'Doro Smartphone', aliases: ['doro'], operatingSystem: 'ANDROID', type: 'PHONE' },
  { manufacturer: 'Doro', family: 'General', model: 'Doro Button Phone', aliases: ['doro knapp'], operatingSystem: 'OTHER', type: 'PHONE' },
  { manufacturer: 'Nokia', family: 'General', model: 'Nokia Smartphone', aliases: ['nokia'], operatingSystem: 'ANDROID', type: 'PHONE' },
  { manufacturer: 'Nokia', family: 'General', model: 'Nokia Button Phone', aliases: ['nokia knapp'], operatingSystem: 'OTHER', type: 'PHONE' }
]

export const deviceCatalogVersion = '2026-09-04'

