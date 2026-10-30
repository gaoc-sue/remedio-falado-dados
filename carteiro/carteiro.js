/*
 * Carteiro do BULA (Cloudflare Workers).
 *
 * Só faz uma coisa: quando um celular manda uma mensagem para o outro (pelo servidor de passagem, criptografada),
 * ele pede aqui para o outro celular "acordar" na hora. O carteiro manda pelo Firebase Cloud Messaging um aviso
 * SEM CONTEÚDO ({t:"acordar"}). O celular acorda e busca a mensagem criptografada sozinho.
 *
 * O carteiro nunca vê remédio, nome ou horário: só o código de notificação do aparelho (token do Firebase).
 * Não guarda nada e não grava log.
 *
 * Segredo necessário (configurado pela publicação automática): FIREBASE_SA = o JSON da conta de serviço do Firebase.
 */

const ESCOPO = 'https://www.googleapis.com/auth/firebase.messaging';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const INTERVALO_MS = 3000; // o mesmo celular não é acordado mais de uma vez a cada 3 segundos

let acesso = null;          // {token, ate}
const ultimos = new Map();  // token do aparelho -> última vez (memória desta instância, sem gravar)

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
  'Cache-Control': 'no-store',
};
const resposta = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS } });

function b64url(bytes) {
  let s = '';
  const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i < b.length; i++) s += String.fromCharCode(b[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
const b64urlTexto = t => b64url(new TextEncoder().encode(t));

function pemParaBytes(pem) {
  const corpo = String(pem).replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  const bin = atob(corpo);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function lerConta(env) {
  let sa;
  try { sa = typeof env.FIREBASE_SA === 'string' ? JSON.parse(env.FIREBASE_SA) : env.FIREBASE_SA; } catch (e) { sa = null; }
  if (!sa || !sa.client_email || !sa.private_key || !sa.project_id) throw new Error('FIREBASE_SA ausente ou incompleto');
  return sa;
}

/* JWT assinado (RS256) trocado por um token de acesso do Google, guardado até 1 minuto antes de vencer */
export async function tokenDeAcesso(sa, agora = Date.now(), f = fetch) {
  if (acesso && acesso.email === sa.client_email && acesso.ate > agora + 60000) return acesso.token;
  const iat = Math.floor(agora / 1000);
  const cab = b64urlTexto(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const corpo = b64urlTexto(JSON.stringify({ iss: sa.client_email, scope: ESCOPO, aud: TOKEN_URL, iat, exp: iat + 3600 }));
  const chave = await crypto.subtle.importKey('pkcs8', pemParaBytes(sa.private_key), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const assinatura = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', chave, new TextEncoder().encode(cab + '.' + corpo));
  const jwt = cab + '.' + corpo + '.' + b64url(assinatura);
  const r = await f(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=' + encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer') + '&assertion=' + jwt,
  });
  if (!r.ok) throw new Error('google token ' + r.status);
  const j = await r.json();
  acesso = { token: j.access_token, ate: agora + (j.expires_in || 3600) * 1000, email: sa.client_email };
  return acesso.token;
}

export async function enviarAcorda(env, tokenAparelho, f = fetch) {
  const sa = lerConta(env);
  const at = await tokenDeAcesso(sa, Date.now(), f);
  const r = await f(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + at },
    // só dados, prioridade alta (acorda o celular mesmo economizando bateria) e válido por 10 minutos
    body: JSON.stringify({ message: { token: tokenAparelho, data: { t: 'acordar' }, android: { priority: 'high', ttl: '600s' } } }),
  });
  let detalhe = '';
  if (!r.ok) { try { const j = await r.json(); detalhe = (j.error && (j.error.status || j.error.message)) || ''; } catch (e) { } }
  return { ok: r.ok, status: r.status, detalhe };
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
    if (req.method === 'GET' && url.pathname === '/saude') {
      let pronto = true;
      try { lerConta(env); } catch (e) { pronto = false; }
      return resposta({ ok: true, firebase: pronto });
    }
    if (req.method !== 'POST' || url.pathname !== '/acordar') return resposta({ erro: 'não encontrado' }, 404);
    if (Number(req.headers.get('content-length') || 0) > 8192) return resposta({ erro: 'grande demais' }, 413);
    let b;
    try { b = await req.json(); } catch (e) { return resposta({ erro: 'json' }, 400); }
    const token = String((b && b.token) || '');
    if (!/^[A-Za-z0-9_:\-]{20,4096}$/.test(token)) return resposta({ erro: 'token' }, 400);
    const agora = Date.now();
    if (agora - (ultimos.get(token) || 0) < INTERVALO_MS) return resposta({ ok: true, agrupado: true });
    ultimos.set(token, agora);
    if (ultimos.size > 5000) ultimos.clear();
    try {
      const r = await enviarAcorda(env, token);
      // UNREGISTERED / NOT_FOUND: o app foi desinstalado ou trocou o código; quem enviou pode esquecer este token
      return resposta({ ok: r.ok, invalido: r.status === 404 || r.detalhe === 'UNREGISTERED' }, r.ok ? 200 : 502);
    } catch (e) {
      return resposta({ ok: false, erro: 'firebase' }, 502);
    }
  },
};
