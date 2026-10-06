// Reduz a imagem escolhida no navegador antes de enviar: a logo fica no
// banco como data URL, então precisa ser pequena (o backend aceita até
// ~300 KB). PNG preserva transparência; se ainda passar do limite, WebP.

const LADO_MAXIMO = 256
export const TAMANHO_MAXIMO_DATA_URL = 400_000

function carregar(arquivo: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(arquivo)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('Não foi possível ler a imagem.'))
    }
    img.src = url
  })
}

export async function reduzirLogo(arquivo: File): Promise<string> {
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(arquivo.type)) {
    throw new Error('Use uma imagem PNG, JPEG ou WebP.')
  }
  const img = await carregar(arquivo)
  const escala = Math.min(1, LADO_MAXIMO / Math.max(img.width, img.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(img.width * escala))
  canvas.height = Math.max(1, Math.round(img.height * escala))
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Seu navegador não conseguiu processar a imagem.')
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)

  let dataUrl = canvas.toDataURL('image/png')
  if (dataUrl.length > TAMANHO_MAXIMO_DATA_URL) dataUrl = canvas.toDataURL('image/webp', 0.85)
  if (dataUrl.length > TAMANHO_MAXIMO_DATA_URL) throw new Error('A imagem ficou grande demais. Tente outra.')
  return dataUrl
}
