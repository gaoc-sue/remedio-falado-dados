/* Remédio Falado — leitura da receita (TESTE).
   Recebe as linhas de texto de uma receita (foto lida no celular, PDF ou QR code) e separa os remédios:
   nome, dose, quanto tomar e em que momento, exatamente como está escrito.
   Não inventa nem sugere dose: o que não está claro vira pergunta, e cada item só é salvo depois do "sim" da pessoa.
   Para tirar a função do app, apague este arquivo e a chave "Ler receita (teste)" em Ajustes. */
(function(G){
  'use strict';
  var semAc = function(s){ return String(s||'').normalize('NFD').replace(/[̀-ͯ]/g,''); };
  var U = function(s){ return semAc(s).toUpperCase(); };
  var soLetras = function(s){ return s.replace(/[^A-Z]/g,''); };

  /* ---------- 1. linhas na ordem de leitura (junta pedaços da mesma altura) ---------- */
  function ordenar(ls){
    var v=[];
    (ls||[]).forEach(function(l,i){
      if(typeof l==='string'){ if(l.trim()) v.push({t:l,x:0,y:i*20,w:0,h:16}); return; }
      if(l && l.t && String(l.t).trim()) v.push({t:String(l.t),x:+l.x||0,y:+l.y||(i*20),w:+l.w||0,h:+l.h||16});
    });
    v.sort(function(a,b){ return (a.y+a.h/2)-(b.y+b.h/2); });
    var rows=[];
    v.forEach(function(l){
      var c=l.y+l.h/2, r=null;
      for(var k=rows.length-1;k>=0 && k>=rows.length-3;k--){
        var q=rows[k]; if(Math.abs(q.c-c) < Math.max(4,Math.min(q.h,l.h)*0.5)){ r=q; break; } }
      if(r){ r.ps.push(l); r.c=(r.c*(r.ps.length-1)+c)/r.ps.length; r.h=Math.max(r.h,l.h); }
      else rows.push({c:c,h:l.h,ps:[l]});
    });
    return rows.map(function(r){
      r.ps.sort(function(a,b){ return a.x-b.x; });
      var x0=Infinity,y0=Infinity,x1=-Infinity,y1=-Infinity;
      r.ps.forEach(function(p){ x0=Math.min(x0,p.x); y0=Math.min(y0,p.y); x1=Math.max(x1,p.x+p.w); y1=Math.max(y1,p.y+p.h); });
      return {t:r.ps.map(function(p){ return p.t; }).join(' '),x:x0,y:y0,w:x1-x0,h:y1-y0};
    });
  }

  /* ---------- 2. limpeza do texto lido ---------- */
  function limpar(t){
    var s=U(t).replace(/[…·_]+/g,' ').replace(/\.{2,}/g,' ').replace(/[:;]{2,}/g,' ').replace(/[“”"«»]/g,' ');
    s=s.replace(/([0-9][0-9O]*)(?=\s*(MCG|MG|ML)\b|\s*(MCG|MG)(?=[^A-Z]|$))/g,function(m){ return m.replace(/O/g,'0'); });
    s=s.replace(/\b(TOMAR|USAR|INGERIR)\s*O(?=\d)/g,'$1 0');
    return s.replace(/\s+/g,' ').trim();
  }
  var NUM={UM:1,UMA:1,DOIS:2,DUAS:2,TRES:3,QUATRO:4,CINCO:5,MEIO:0.5,MEIA:0.5};
  function numero(t){
    t=String(t||'').replace(/\s+/g,'');
    if(NUM[t]!=null) return NUM[t];
    var f=t.match(/^(\d+)\/(\d+)$/); if(f) return +f[2] ? +f[1]/+f[2] : null;
    var n=parseFloat(t.replace(',','.')); return isNaN(n)?null:n;
  }

  /* ---------- 3. dicionário: princípios ativos e nomes de caixa da lista da Anvisa ---------- */
  var SAL=/\b(CLORIDRATO|DICLORIDRATO|BROMIDRATO|MALEATO|DIMALEATO|BESILATO|MESILATO|DIMESILATO|SUCCINATO|TARTARATO|HEMITARTARATO|FUMARATO|HEMIFUMARATO|CITRATO|ACETATO|SULFATO|BISSULFATO|FOSFATO|TOSILATO|OXALATO|VALERATO|PROPIONATO|ESTEARATO|BENZOATO|LACTATO|GLUCONATO|NITRATO|POTASSIC[AO]|SODIC[AO]|CALCIC[AO]|MAGNESIC[AO]|MONOIDRATAD[AO]|DI-?HIDRATAD[AO]|TRI-?HIDRATAD[AO]|SESQUI-?HIDRATAD[AO]|HEMI-?HIDRATAD[AO]|HIDRATAD[AO]|ANIDR[AO]|MICRONIZAD[AO]|DE|DA|DO|DAS|DOS)\b/g;
  var GENERICAS=/^(ACIDO|VITAMINA|OLEO|EXTRATO|SAIS|SAL|COMPLEXO|AGUA|SOLUCAO|TINTURA|FERRO|POLIVITAMINICO|INSULINA|TOXINA|VACINA)$/;
  /* palavras da receita que não são nome de remédio */
  var COMUNS=/^(TOMAR|COMPRIMIDO|COMPRIMIDOS|CAPSULA|CAPSULAS|CONTINUO|MANHA|NOITE|JANTAR|ALMOCO|HORAS|DIARIO|DIARIA|QUANTIDADE|RECEITUARIO|RECEITA|SECRETARIA|SAUDE|ESTADO|HOSPITAL|PACIENTE|PRONTUARIO|UNIDADE|MEDICO|MEDICA|DEITAR|DORMIR|JEJUM|ANTES|DEPOIS|APOS|USAR|APLICAR|INGERIR|SEMANA|ORIENTACAO|CONTROLE|ESPECIAL|SIMPLES|ORAL|TOPICO|ENDERECO|CIDADE|ASSINATURA|CARIMBO|FARMACIA|FAMILIA|POPULAR|PREFEITURA|MUNICIPAL|NACIONAL|BRASIL|RECIFE|PERNAMBUCO|AMAZONAS|ESTRADA|ARRAIAL|AMARELA|CASA|SILVA|SANTOS|SOUZA|OLIVEIRA|MARIA|JOSE|JOAO|ANDAR|CELULAS|TRONCO)$/;
  var DIC=null;
  function dicionario(base){
    if(DIC && DIC.base===base) return DIC;
    var sub={}, marca={}, curta={}, porSub={};
    var addSub=function(k,si){ k=soLetras(k); if(k.length<5||COMUNS.test(k)) return; (sub[k]=sub[k]||[]).indexOf(si)<0 && sub[k].push(si); };
    (base.substancias||[]).forEach(function(s,si){
      var partes=U(s).split(';');
      partes.forEach(function(p){
        var w=p.replace(SAL,' ').replace(/[^A-Z ]/g,' ').trim().split(/\s+/).filter(Boolean);
        if(!w.length) return;
        addSub(w.join(''),si);
        if(!GENERICAS.test(w[0])) addSub(w[0],si);
      });
    });
    (base.itens||[]).forEach(function(r,idx){
      (porSub[r[0]]=porSub[r[0]]||[]).push(idx);
      var p=U(r[1]).replace(/[®™]/g,'').replace(/[^A-Z0-9 ]/g,' ').trim(), w=p.split(/\s+/)[0]||'';
      w=soLetras(w); if(w.length>=3 && w.length<5 && p.split(/\s+/)[0]===w && !sub[w]) (curta[w]=curta[w]||[]).push(idx);
      if(w.length<5||COMUNS.test(w)||sub[w]||new RegExp('^('+SAL.source.slice(3,-3)+')$').test(w)) return;
      (marca[w]=marca[w]||[]).push(idx);
    });
    var chaves=Object.keys(sub).sort(function(a,b){ return b.length-a.length; });
    var marcas=Object.keys(marca).sort(function(a,b){ return b.length-a.length; });
    DIC={base:base,sub:sub,marca:marca,curta:curta,porSub:porSub,chaves:chaves,marcas:marcas};
    return DIC;
  }
  /* texto sem espaços com o mapa de volta para a posição original (o leitor às vezes cola as palavras) */
  function compacto(s){ var c='',m=[]; for(var i=0;i<s.length;i++){ if(/[A-Z]/.test(s[i])){ c+=s[i]; m.push(i); } } return {c:c,m:m}; }
  function palavraEm(s,i){ var a=i,b=i; while(a>0 && /[A-Z0-9]/.test(s[a-1])) a--; while(b<s.length && /[A-Z0-9]/.test(s[b])) b++; return s.slice(a,b); }
  /* acha nomes de remédio numa linha. Nome de caixa só no começo do item ou depois de "OU"/"+". */
  function acharNomes(s,D){
    var cp=compacto(s), achados=[], usado=[];
    var livre=function(a,b){ for(var i=a;i<b;i++) if(usado[i]) return false; return true; };
    var marcar=function(a,b){ for(var i=a;i<b;i++) usado[i]=1; };
    var ok=function(k,p){ var o=cp.m[p], o2=cp.m[p+k.length-1]; var pal=palavraEm(s,o);
      var ini = o===0 || !/[A-Z]/.test(s[o-1]) || pal.length>=k.length+4;
      var fim = o2===s.length-1 || !/[A-Z]/.test(s[o2+1]) || pal.length>=k.length+3 || /^(XR|LP|MR|CR|SR|XL|AP|RETARD)/.test(s.slice(o2+1));
      return ini && fim; };
    D.chaves.forEach(function(k){
      var p=cp.c.indexOf(k);
      while(p>=0){ if(livre(p,p+k.length) && ok(k,p)){ achados.push({tipo:'sub',k:k,p:p,fim:p+k.length,o:cp.m[p],of:cp.m[p+k.length-1]+1}); marcar(p,p+k.length); }
        p=cp.c.indexOf(k,p+1); }
    });
    D.marcas.forEach(function(k){
      var p=cp.c.indexOf(k);
      while(p>=0){
        var antes=cp.c.slice(0,p);
        var lugar = p===0 || /(OU|E)$/.test(antes) && achados.some(function(a){ return a.fim<=p; }) || /\+\s*$/.test(s.slice(0,cp.m[p]));
        if(lugar && livre(p,p+k.length) && ok(k,p)){ achados.push({tipo:'marca',k:k,p:p,fim:p+k.length,o:cp.m[p],of:cp.m[p+k.length-1]+1}); marcar(p,p+k.length); }
        p=cp.c.indexOf(k,p+1); }
    });
    if(!achados.length){ var w0=(s.match(/^[A-Z]{3,4}(?=[^A-Z]|$)/)||[])[0]; if(w0 && D.curta[w0]){ D.marca[w0]=D.curta[w0]; achados.push({tipo:'marca',k:w0,p:0,fim:w0.length,o:0,of:w0.length}); } }
    return achados.sort(function(a,b){ return a.p-b.p; });
  }

  /* ---------- 4. tipos de linha ---------- */
  var CAB=/^(NOME|PACIENTE|PRONT|DATA|CRM|FONE|TEL|ENDERE|END\b|RUA\b|ESTRADA|AV\b|AV\.|AVENIDA|CEP|SECRETARIA|HOSPITAL|RECEITU|UNIDADE|DR\b|DR\.|DRA\b|DRA\.|MEDIC|ASSINAT|CPF|CNS|CART|IDADE|SEXO|ESTADO D|PREFEITURA|GOVERNO|SUS\b|SISTEMA UNICO|\d+\s*[°ºO]?\s*ANDAR|EMITENTE|IDENTIFICA|COMPRADOR|FORNECEDOR|VIA\b|1A VIA|2A VIA|CARIMBO)/;
  var RODAPE=/\bCRM\b|CNPJ|FONE|TELEFONE|@|WWW\.|HTTP|ASSINATURA|CARIMBO|\bANDAR\b/;
  var VERBO=/^(TOMAR|USAR|APLICAR|INGERIR|ADMINISTRAR|PINGAR|DILUIR|MASTIGAR|COLOCAR|FAZER|DAR|POSOLOGIA|MODO DE USO|CHUPAR|DISSOLVER|INALAR|PASSAR)\b/;
  var NUMERO=/^\s*(\d{1,2})\s*[-.)–:]\s*(?:\d\s+)?/;
  var QTD_LINHA=/^(QUANTIDADE|QTD|QTDE|QUANT\b|TOTAL\b|\d+\s*\(?\s*[A-Z]+\s*\)?\s*CAIXAS?|CAIXAS?\b|EMBALAGE|USO CONTINUO$|USO ORAL$|USO INTERNO$|USO EXTERNO$|VIA ORAL)/;

  /* ---------- 5. instrução: quanto e quando ---------- */
  var UNID='(COMPRIMIDOS?|COMP\\b\\.?|CPS?\\b\\.?|CAPSULAS?|CAPS?\\b\\.?|DRAGEAS?|GOTAS?|GTS\\b|ML\\b|MEDIDAS?|DOSES?|SACHES?|ENVELOPES?|JATOS?|PUFFS?|UNIDADES?|COLHER(?:ES)?|AMPOLAS?)';
  var QTD=new RegExp('(\\d+\\s*\\/\\s*\\d+|\\d+(?:[.,]\\d+)?|\\bUMA?\\b|\\bDOIS\\b|\\bDUAS\\b|\\bTRES\\b|\\bQUATRO\\b|\\bMEI[OA]\\b)\\s*(?:E\\s*(MEI[OA]|1\\s*\\/\\s*2))?\\s*\\(?\\s*(?:\\d+\\s*\\))?\\s*'+UNID,'g');
  var MOMS=[
    ['cafe',/PELA\s*MANHA|DE\s*MANHA|NA\s*MANHA|\bA\s*MANHA|PELA\s*MANH|MANHA|JEJUM|AO\s*ACORDAR|CAFE/g],
    ['almoco',/ALMOCO|MEIO\s*-?\s*DIA/g],
    ['tarde',/\bA\s*TARDE|PELA\s*TARDE|DE\s*TARDE|\bTARDE\b/g],
    ['jantar',/JANTAR|\bJANTA\b/g],
    ['dormir',/DEITAR|DORMIR/g],
    ['noite',/NOITE/g]];
  function achar(re,s){ var r=[],m; re.lastIndex=0; while((m=re.exec(s))){ r.push({i:m.index,f:m.index+m[0].length,m:m}); if(!re.global) break; } return r; }
  function instrucao(s){
    var r={qtds:[],moms:[],freq:null,intervalo:null,sos:false,semanal:false,dias:null,continuo:/USO\s*CONTINUO/.test(s),unidade:null};
    achar(QTD,s).forEach(function(x){ var n=numero(x.m[1]); if(n==null||n<=0||n>40) return; if(x.m[2] || /^\s*E\s*MEI[OA]\b/.test(s.slice(x.f))) n+=0.5; r.qtds.push({i:x.i,f:x.f,n:n}); if(!r.unidade) r.unidade=x.m[3]; });
    var vistos=[];
    MOMS.forEach(function(par){ achar(par[1],s).forEach(function(x){ if(vistos.some(function(v){ return x.i<v.f && x.f>v.i; })) return; vistos.push(x); r.moms.push({k:par[0],i:x.i,f:x.f}); }); });
    /* "à noite, antes de dormir" e "à noite, no jantar": vale o mais exato */
    r.moms=r.moms.filter(function(m){ return m.k!=='noite' || !r.moms.some(function(o){ return (o.k==='jantar'||o.k==='dormir') && Math.abs(o.i-m.i)<30; }); });
    r.moms.sort(function(a,b){ return a.i-b.i; });
    /* quantidade solta antes do momento: "1 comprimido pela manhã e 2 à noite" */
    r.moms.forEach(function(m){ var antes=s.slice(Math.max(0,m.i-14),m.i), q=antes.match(/(?:^|\s|E)(\d+(?:[.,]\d+)?|\d\/\d|UMA?|DOIS|DUAS|MEI[OA])\s*(?:A|AO|PELA|NO|NA|DE|APOS|ANTES|DO|DA)?\s*(?:O|A)?\s*$/);
      if(q && !r.qtds.some(function(x){ return x.f>m.i-14 && x.i<m.i; })){ var n=numero(q[1]); if(n!=null && n>0 && n<=40) r.qtds.push({i:m.i-1,f:m.i,n:n}); } });
    r.qtds.sort(function(a,b){ return a.i-b.i; });
    var iv=s.match(/(?:DE\s*)?(?:^|[^\d\/])(\d{1,2})\s*\/\s*(\d{1,2})\s*(?:H\b|HS\b|HORAS?|HRS?\b|H(?=[^A-Z]|$))/) || s.match(/\bDE\s*(\d{1,2})\s*\/\s*(\d{1,2})\b(?!\s*\/)/) || s.match(/DE\s*(\d{1,2})\s*EM\s*(\d{1,2})\s*H/);
    if(iv && +iv[1]===+iv[2] && +iv[1]>0){ r.intervalo=+iv[1]; r.freq=Math.round(24/+iv[1]); }
    var fx=s.match(/(\d|UMA|DUAS|TRES|QUATRO)\s*(?:X|VEZ(?:ES)?)\s*(?:AO|POR|NO|AO\s*)?\s*DIA/);
    if(fx){ r.freq=numero(fx[1]); r.intervalo=null; }
    else if(!r.freq && /\b(AO|POR)\s*DIA\b|DIARIAMENTE|1\s*X\s*DIA/.test(s)) r.freq=1;
    r.sos=/\bSE\s*(DOR|FEBRE|NECESSARIO|PRECISAR|NAUSEA|ENJOO|VOMITO|FALTA\s*DE\s*AR|CRISE|INSONIA|TOSSE|COCEIRA|ANSIEDADE|PRESSAO)|S\.?\s*O\.?\s*S\b|QUANDO\s*(NECESSARIO|PRECISAR|TIVER|HOUVER|SENTIR)|EM\s*CASO\s*DE|A\s*CRITERIO|SE\s*NECESS/.test(s);
    r.semanal=/SEMANA|SEMANAL|DIAS\s*ALTERNADOS|DIA\s*SIM|DIA\s*NAO|MENSAL|POR\s*MES|A\s*CADA\s*\d+\s*DIAS|\b(SEGUNDA|TERCA|QUARTA|QUINTA|SEXTA|SABADO|DOMINGO)S?\b/.test(s) && !/POR\s*\d+\s*SEMANAS?/.test(s);
    var d=s.match(/POR\s*(\d+)\s*(DIAS?|SEMANAS?|MES(?:ES)?)/); if(d) r.dias=d[1]+' '+d[2].toLowerCase().replace('mes','mês').replace('meses','meses');
    return r;
  }

  /* ---------- 6. remédio: qual item da lista da Anvisa ---------- */
  var DOSE=/(\d+(?:[.,]\d+)?)\s*(MCG|MG|G(?![A-Z])|UI|%)(?:\s*\/\s*(ML|G|GOTA|DOSE))?/;
  function mg(v,u){ v=parseFloat(String(v).replace(/\.(?=\d{3}\b)/,'').replace(',','.')); if(isNaN(v)) return null; return u==='G'?v*1000:u==='MCG'?v/1000:v; }
  function doseItem(apres){ var m=U(apres).match(/^([\d.,]+)\s*(MCG|MG|G)\b/); return m?mg(m[1],m[2]):null; }
  function fmt(n){ return n<1 ? String(Math.round(n*1000*100)/100).replace('.',',')+' mcg' : String(Math.round(n*1000)/1000).replace('.',',')+' mg'; }
  function escolher(D,base,nome,flags,mgR,forma){
    var cands=[];
    if(nome.tipo==='sub'){
      var sis=D.sub[nome.k]||[], simples=sis.filter(function(si){ return String(base.substancias[si]).indexOf(';')<0; });
      (simples.length?simples:sis).forEach(function(si){ (D.porSub[si]||[]).forEach(function(i){ cands.push(i); }); });
    } else cands=(D.marca[nome.k]||[]).slice();
    if(!cands.length) return null;
    var lib=function(i){ return /LIB\s*(PROL|MODIF|RETARD|CONT)|\bXR\b|\bLP\b|\bRETARD\b|\bCR\b|\bSR\b|\bXL\b|\bMR\b/.test(U(base.itens[i][1])+' '+U(base.itens[i][2])); };
    var oral=function(i){ return /\b(COM|CAP|DRG|COMP|CAPS)\b|COM REV|CAP DURA|CAP GEL/.test(U(base.itens[i][2])); };
    var nota=function(i){ var a=U(base.itens[i][2]), p=U(base.itens[i][1]), n=0;
      if(mgR!=null && doseItem(a)===mgR) n+=100;
      if(flags.length) n+= lib(i)?30:-30; else n+= lib(i)?-10:0;
      flags.forEach(function(f){ if(p.indexOf(f)>=0) n+=15; });
      if(forma==='CAP' && /\bCAP/.test(a)) n+=10; if(forma==='COM' && /\bCOM\b|COM REV/.test(a)) n+=10;
      if(oral(i)) n+=8; if(+base.itens[i][6]) n-=20; /* uso hospitalar */
      if(nome.tipo==='sub' && soLetras(p).indexOf(nome.k)===0) n+=12; else if(nome.tipo==='sub' && soLetras(p).indexOf(nome.k)>0) n+=6; /* prefere a caixa com o nome do princípio ativo (genérico) */
      return n; };
    cands.sort(function(a,b){ return nota(b)-nota(a); });
    var doses=[]; cands.filter(function(i){ return oral(i) && (!flags.length||lib(i)); }).forEach(function(i){ var d=doseItem(base.itens[i][2]); if(d!=null && doses.indexOf(d)<0) doses.push(d); });
    doses.sort(function(a,b){ return a-b; });
    return {idx:cands[0], achouDose: mgR!=null && doseItem(base.itens[cands[0]][2])===mgR, doses:doses.slice(0,8)};
  }
  /* nome lido com 1 letra errada (2 nos nomes longos): "SORAFENINE" -> SORAFENIBE. A pessoa confere na tela. */
  function dist(a,b,max){ if(Math.abs(a.length-b.length)>max) return max+1; var p=[],i,j; for(j=0;j<=b.length;j++) p[j]=j;
    for(i=1;i<=a.length;i++){ var c=[i], menor=i; for(j=1;j<=b.length;j++){ c[j]=Math.min(p[j]+1,c[j-1]+1,p[j-1]+(a[i-1]===b[j-1]?0:1)); if(c[j]<menor) menor=c[j]; } if(menor>max) return max+1; p=c; } return p[b.length]; }
  function aproximar(texto,D){
    var r=[], re=/[A-Z]{6,}/g, m;
    while((m=re.exec(texto))){ var w=m[0]; if(COMUNS.test(w)) continue; var max=w.length>=10?2:1, melhor=null, md=max+1;
      for(var i=0;i<D.chaves.length;i++){ var k=D.chaves[i]; if(Math.abs(k.length-w.length)>max) continue; var d=dist(w,k,max); if(d<md){ md=d; melhor=k; if(!d) break; } }
      if(melhor){ r.push({tipo:'sub',k:melhor,p:m.index,fim:m.index+w.length,o:m.index,of:m.index+w.length,lido:w}); break; } }
    return r;
  }
  function remedios(texto,D,base,podeAproximar){
    var achados=acharNomes(texto,D);
    if(!achados.length && podeAproximar) achados=aproximar(texto,D);
    if(!achados.length) return null;
    var grupos=[[achados[0]]], alternativa=false;
    for(var i=1;i<achados.length;i++){
      var entre=texto.slice(achados[i-1].of,achados[i].o);
      if(/^\s*(DE|DA|DO)\s*$/.test(entre)) continue; /* "ALENDRONATO DE SODIO": o segundo nome é só o sal */
      if(/\bOU\b|^OU$|OU\s*$/.test(entre.replace(/[\d.,]+\s*(MG|MCG|G)\b/g,' ').trim()) || /^OU/.test(entre.trim())){ alternativa=true; grupos.push([achados[i]]); }
      else grupos[grupos.length-1].push(achados[i]);
    }
    var flagsDe=function(s){ var f=[]; ['XR','LP','MR','CR','SR','XL','RETARD','AP'].forEach(function(x){ if(new RegExp('(^|[^A-Z])'+x+'([^A-Z]|$)').test(s) || new RegExp('[A-Z]{4,}'+x+'(\\d|\\s|$)').test(s)) f.push(x); }); return f; };
    var forma=/CAPSULA|\bCAPS?\b/.test(texto)?'CAP':/COMPRIMIDO|\bCOMP\b|\bCP\b/.test(texto)?'COM':'';
    var fimNomes=achados[achados.length-1].of;
    var opcoes=grupos.map(function(g,gi){
      var ini=g[0].o, fimG = gi+1<grupos.length ? grupos[gi+1][0].o : texto.length;
      var trecho=texto.slice(ini,fimG), dm=trecho.match(DOSE);
      if(!dm && gi+1<grupos.length){ dm=texto.slice(fimNomes).match(DOSE); }   /* "SELOZOK OU METOPROLOL 25MG": a dose vale para os dois */
      var mgR = dm && dm[2]!=='%' && dm[2]!=='UI' && !dm[3] ? mg(dm[1],dm[2]) : null;
      var flags=flagsDe(trecho.slice(0, dm? trecho.indexOf(dm[0])+dm[0].length+6 : 40));
      var combo=null;
      if(g.length>1){ /* associação: "LOSARTANA + HIDROCLOROTIAZIDA" */
        var conj=g.map(function(a){ return a.k; });
        base.substancias.some(function(s,si){ var partes=U(s).split(';'); if(partes.length!==conj.length) return false;
          var okk=conj.every(function(k){ return partes.some(function(p){ return soLetras(p.replace(SAL,' ')).indexOf(k)>=0; }); });
          if(okk){ combo=si; return true; } return false; });
      }
      var esc;
      if(combo!=null){ var lst=D.porSub[combo]||[]; esc= lst.length?{idx:lst[0],achouDose:false,doses:[],combo:true}:null; if(esc) esc.dosesTxt=(texto.slice(ini,fimG).match(/[\d.,]+\s*(MG|MCG|G)\b/g)||[]).map(function(x){ return x.replace(/\s*(MCG|MG|G)$/,function(u){ return ' '+u.trim().toLowerCase(); }); }).join(' + '); mgR=null; }
      else esc=escolher(D,base,g[0],flags,mgR,forma);
      return esc ? {nome:g.map(function(a){ return a.k; }).join(' + '),lido:g[0].lido||'',tipo:g[0].tipo,idx:esc.idx,mg:mgR,dose:esc.dosesTxt||(mgR!=null?fmt(mgR):''),doseTxt:esc.dosesTxt||'',achouDose:esc.achouDose,doses:esc.doses||[],flags:flags,combo:!!esc.combo,fim:fimG} : null;
    }).filter(Boolean);
    if(!opcoes.length) return null;
    return {opcoes:opcoes,alternativa:alternativa && opcoes.length>1, fimNome:fimNomes};
  }

  /* ---------- 7. a receita inteira ---------- */
  function ler(linhasBrutas, base){
    var D=dicionario(base), rows=ordenar(linhasBrutas), itens=[], at=null;
    var fechar=function(){ if(at) itens.push(at); at=null; };
    rows.forEach(function(r){
      var s=limpar(r.t); if(!s) return;
      var semNum=s.replace(NUMERO,''), numerada=NUMERO.test(s) && !/^\d{1,2}\s*[\/.]\s*\d{1,2}\s*[\/.]\s*\d{2,4}/.test(s);
      if(CAB.test(semNum) || (RODAPE.test(s) && !VERBO.test(semNum))){ if(at && RODAPE.test(s)) at.parou=true; return; }
      var verbo=VERBO.test(semNum);
      var rem = verbo ? null : remedios(semNum,D,base,numerada||DOSE.test(semNum)||/COMPRIMID|CAPSUL|DRAGEA|XAROPE|\bGOTAS\b|ATO DE\b|IDRATO DE\b/.test(semNum));
      if(rem || (numerada && !verbo && /[A-Z]{3,}/.test(semNum))){
        fechar();
        at={n:itens.length+1,rows:[r],nomeTxt:semNum,rem:rem,instr:rem? semNum.slice(rem.fimNome):'',parou:false};
        if(rem){ var dz=semNum.slice(rem.fimNome).match(DOSE); if(dz) at.instr=semNum.slice(rem.fimNome+dz.index+dz[0].length);
          /* na linha do nome, "60 comprimidos" costuma ser quanto comprar, não quanto tomar */
          var vb=at.instr.search(/\b(TOMAR|USAR|INGERIR|APLICAR|ADMINISTRAR|MASTIGAR)\b/);
          if(vb>=0) at.instr=at.instr.slice(vb);
          else at.instr=at.instr.replace(QTD,function(m0,n0){ var n=numero(n0); return n!=null && n>=10 ? ' ' : m0; }); }
        return;
      }
      if(!at || at.parou) return;
      if(QTD_LINHA.test(semNum)){ at.rows.push(r); return; }
      at.rows.push(r); at.instr+=' '+semNum;
    });
    fechar();
    return itens.map(function(it){ return montar(it,base); });
  }
  function montar(at,base){
    var x0=Infinity,y0=Infinity,x1=-Infinity,y1=-Infinity;
    at.rows.forEach(function(r){ if(!r.w) return; x0=Math.min(x0,r.x); y0=Math.min(y0,r.y); x1=Math.max(x1,r.x+r.w); y1=Math.max(y1,r.y+r.h); });
    var ins=instrucao(at.instr.replace(/\s+/g,' ').trim());
    var o={n:at.n,texto:at.rows.map(function(r){ return r.t.replace(/\.{3,}|…+/g,' … ').replace(/\s+/g,' ').trim(); }).join('\n'),
      caixa: x1>x0 ? {x:x0,y:y0,w:x1-x0,h:y1-y0} : null,
      opcoes: at.rem? at.rem.opcoes : [], alternativa: !!(at.rem&&at.rem.alternativa), desconhecido: !at.rem,
      nomeLido: at.nomeTxt.replace(/[\d.,]+\s*(MG|MCG|G)\b.*$/,'').replace(/\s+/g,' ').trim(),
      sos:ins.sos, semanal:ins.semanal, dias:ins.dias, continuo:ins.continuo, freq:ins.freq, intervalo:ins.intervalo,
      momentos:[], qtd:null, perguntas:[]};
    /* quanto em cada momento: a quantidade escrita logo antes; senão, a primeira */
    var q0 = ins.qtds.length ? ins.qtds[0].n : null; o.qtd=q0;
    var qDe=function(i){ var q=null; ins.qtds.forEach(function(x){ if(x.i<i) q=x.n; }); return q!=null?q:q0; };
    var ks=[];
    ins.moms.forEach(function(m){ if(!ks.some(function(k){ return k.k===m.k; })) ks.push({k:m.k,q:qDe(m.i)}); });
    if(ins.intervalo && !ks.length){ o.momentos=[{k:'cafe',q:q0,mais:0}]; for(var i=1;i<ins.freq;i++) o.momentos.push({k:null,q:q0,mais:i*ins.intervalo}); }
    else if(ks.length){ o.momentos=ks; }
    else if(ins.freq===2) o.momentos=[{k:'cafe',q:q0},{k:'jantar',q:q0}];
    else if(ins.freq===3) o.momentos=[{k:'cafe',q:q0},{k:'almoco',q:q0},{k:'jantar',q:q0}];
    else if(ins.freq===4) o.momentos=[{k:'cafe',q:q0},{k:'almoco',q:q0},{k:'jantar',q:q0},{k:'dormir',q:q0}];
    o.momentos.forEach(function(m){ if(m.k==='noite') o.perguntas.push('noite'); if(m.k==='tarde') o.perguntas.push('tarde'); });
    if(!o.momentos.length && !o.sos && !o.semanal) o.perguntas.push(ins.freq===1?'momento':'hora');
    if(ins.freq && ks.length && ins.freq!==ks.length && !(ins.freq===1 && ks.length===1)) o.perguntas.push('hora');
    if(ins.freq>4 && !ks.length) o.perguntas.push('hora');
    if(q0==null && !o.sos) o.perguntas.push('qtd');
    if(o.alternativa) o.perguntas.unshift('qual');
    else if(o.opcoes[0] && !o.opcoes[0].combo && o.opcoes[0].mg==null){ if(o.opcoes[0].doses.length===1){ o.opcoes[0].mg=o.opcoes[0].doses[0]; o.opcoes[0].dose=fmt(o.opcoes[0].doses[0]); o.opcoes[0].doseDaLista=true; } else o.perguntas.unshift('dose'); }
    o.unidade = ins.unidade ? (/CAP/.test(ins.unidade)?'CAP':/GOT|GTS/.test(ins.unidade)?'GOT':/ML|MEDIDA|COLHER/.test(ins.unidade)?'ML':'COM') : '';
    return o;
  }
  G.RFReceita={ler:ler, limpar:limpar, ordenar:ordenar, instrucao:instrucao, dicionario:dicionario};
})(typeof window!=='undefined'?window:globalThis);
