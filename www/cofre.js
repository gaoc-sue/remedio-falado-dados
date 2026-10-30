/* Cofre: criptografa os dados do app guardados no celular (AES-256-GCM, com a Web Crypto do próprio aparelho).
   No app instalado, a chave fica guardada pelo Android Keystore (plugin nativo "Cofre") e não pode ser copiada.
   No navegador (demonstração), a chave fica no próprio navegador.
   Dados antigos, salvos sem criptografia, são criptografados na primeira abertura. */
(function(){
  'use strict';
  var PREFIXO='rfc1:', CHAVE_WEB='remedio-falado-chave';
  var subtle = window.crypto && window.crypto.subtle;
  var chave=null, cache={}, fila={}, rodando=false, aoErro=null, esperas=[];
  var info={ativo:false, keystore:false, perdido:false, erro:''};

  function b64(buf){ var b=new Uint8Array(buf), s='', n=0x8000; for(var i=0;i<b.length;i+=n) s+=String.fromCharCode.apply(null, b.subarray(i,i+n)); return btoa(s); }
  function deB64(t){ var s=atob(t), b=new Uint8Array(s.length); for(var i=0;i<s.length;i++) b[i]=s.charCodeAt(i); return b; }
  function plugin(){ var C=window.Capacitor; if(!(C && C.isNativePlatform && C.isNativePlatform())) return null;
    return (C.Plugins && C.Plugins.Cofre) || (C.registerPlugin ? C.registerPlugin('Cofre') : null); }
  function lerLS(k){ try{ return localStorage.getItem(k); }catch(e){ return null; } }

  async function chaveBruta(){
    var p=plugin();
    if(p){ try{ var r=await p.chave(); if(r && r.chave){ info.keystore=true; return deB64(r.chave); } }catch(e){ info.erro='Keystore indisponível'; } }
    var t=lerLS(CHAVE_WEB);
    if(t) return deB64(t);
    var k=crypto.getRandomValues(new Uint8Array(32));
    try{ localStorage.setItem(CHAVE_WEB, b64(k)); }catch(e){}
    return k;
  }
  async function cifrar(txt){
    var iv=crypto.getRandomValues(new Uint8Array(12));
    var ct=await subtle.encrypt({name:'AES-GCM', iv:iv}, chave, new TextEncoder().encode(txt));
    return PREFIXO+b64(iv)+':'+b64(ct);
  }
  async function decifrar(v){
    var p=v.slice(PREFIXO.length).split(':');
    var pt=await subtle.decrypt({name:'AES-GCM', iv:deB64(p[0])}, chave, deB64(p[1]));
    return new TextDecoder().decode(pt);
  }
  async function abrir(chaves){
    if(subtle && window.TextEncoder){
      try{ var raw=await chaveBruta(); chave=await subtle.importKey('raw', raw, {name:'AES-GCM'}, false, ['encrypt','decrypt']); info.ativo=true; }
      catch(e){ info.ativo=false; info.erro=String((e && e.message) || e); }
    }
    for(var i=0;i<chaves.length;i++){
      var k=chaves[i], v=lerLS(k);
      if(v && v.indexOf(PREFIXO)===0){
        if(info.ativo){ try{ cache[k]=await decifrar(v); }catch(e){ cache[k]=null; info.perdido=true; } }
        else { cache[k]=null; info.perdido=true; }
      } else {
        cache[k]=v;
        if(v!=null && info.ativo) gravar(k, v); // migra o que estava guardado sem criptografia
      }
    }
    return info;
  }
  function gravar(k, txt){
    cache[k]=txt;
    if(!info.ativo){ try{ if(txt==null) localStorage.removeItem(k); else localStorage.setItem(k, txt); }catch(e){ if(aoErro) aoErro(e); } return; }
    fila[k]=txt; if(!rodando) correr();
  }
  async function correr(){
    rodando=true;
    try{
      while(Object.keys(fila).length){
        var k=Object.keys(fila)[0], t=fila[k]; delete fila[k];
        try{ if(t==null) localStorage.removeItem(k); else localStorage.setItem(k, await cifrar(t)); }catch(e){ if(aoErro) aoErro(e); }
      }
    } finally { rodando=false; var w=esperas; esperas=[]; w.forEach(function(f){ f(); }); }
  }
  function ler(k){ return (k in cache) ? cache[k] : lerLS(k); }
  function esvaziar(){ return (rodando || Object.keys(fila).length) ? new Promise(function(ok){ esperas.push(ok); }) : Promise.resolve(); }

  window.RFCofre={ abrir:abrir, ler:ler, gravar:gravar, esvaziar:esvaziar,
    info:function(){ return info; }, aoErro:function(f){ aoErro=f; } };
})();
