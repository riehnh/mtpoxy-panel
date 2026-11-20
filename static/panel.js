'use strict';
const $ = id => document.getElementById(id);
let csrf = '', authenticated = false, poll = null, lastSites = '', touchedSettings = false;
function notice(message) { $('notice').textContent = message; clearTimeout(notice.timeout); notice.timeout = setTimeout(() => $('notice').textContent = '', 7000); }
async function api(path, data) {
  const response = await fetch('/manage/api/' + path, {method:data ? 'POST':'GET', credentials:'same-origin', headers:data ? {'Content-Type':'application/json','X-CSRF-Token':csrf}:{}, body:data ? JSON.stringify(data):undefined});
  const result = await response.json();
  if (response.status === 401 && path !== 'login') { showLogin(); }
  if (!response.ok) throw new Error(result.error || 'Ошибка запроса');
  return result;
}
function showLogin() { authenticated = false; clearInterval(poll); $('login').hidden = false; $('workspace').hidden = true; $('logout').hidden = true; }
function node(tag, text, className) { const result = document.createElement(tag); if(text)result.textContent=text; if(className)result.className=className; return result; }
function renderSites(sites) {
  const encoded = JSON.stringify(sites); if(encoded===lastSites)return; lastSites=encoded;
  $('count').textContent=sites.length ? sites.length+' '+({one:'подключение',few:'подключения',many:'подключений',other:'подключения'}[new Intl.PluralRules('ru').select(sites.length)]):'';
  $('sites').replaceChildren();
  if (!sites.length) { $('sites').append(node('div','Здесь появится ваш первый WEB-proxy.','card empty')); return; }
  for (const site of [...sites].reverse()) {
    const card=node('article',null,'card'), top=node('div',null,'site-top');
    top.append(node('div',site.hostname,'site-name'),node('span',site.status==='active'?'Готово':site.status==='error'?'Нужен повтор':'Создаётся','state'));
    card.append(top);
    if(site.status!=='active'||site.warning)card.append(node('p',site.warning||site.message,'site-message'));
    const actions=node('div',null,'actions');
    if(site.status==='active') {
      const open=node('a','Подключить в Telegram','button primary'); const link=new URL(site.link); open.href='tg://webproxy?'+link.searchParams.toString();
      const copy=node('button','Скопировать ссылку'); copy.onclick=async()=>{try{await navigator.clipboard.writeText(site.link);notice('Ссылка скопирована');}catch(e){notice('Буфер обмена недоступен. Откройте ссылку в Telegram.');}};
      const website=node('a','Сайт рецептов','button');website.href=site.site_url;website.target='_blank';website.rel='noopener';
      actions.append(open,copy,website);
    } else if(site.status==='error') {
      const retry=node('button','Повторить'); retry.onclick=async()=>{retry.disabled=true;try{await api('create',{retry_id:site.id});await state();}catch(e){notice(e.message);}finally{retry.disabled=false;}};actions.append(retry);
    }
    card.append(actions);$('sites').append(card);
  }
}
async function state() {
  if(!authenticated)return;
  const data=await api('state');
  if(!touchedSettings) {
    $('email').value=data.settings.email;
    $('socksHost').value=data.settings.socks_host||'';
    $('socksPort').value=data.settings.socks_port||'';
    $('socksUser').value=data.settings.socks_user||'';
    touchedSettings=true;
  }
  $('tokenState').textContent=data.token_configured?'Токен сохранён. Новое значение заменит его.':'Токен ещё не указан.';
  $('token').placeholder=data.token_configured?'Оставьте пустым, чтобы сохранить':'Вставьте HTTP token';
  $('serverIp').textContent='IP сервера · '+data.settings.public_ip;
  $('working').hidden=!data.busy;
  $('setupHint').hidden=!!(data.token_configured&&data.settings.email);
  $('createButton').disabled=data.busy||!data.token_configured||!data.settings.email;
  $('createButton').textContent=data.busy?'Создаём…':'Создать прокси';
  $('settingsForm').querySelector('button').disabled=data.busy;
  renderSites(data.sites);
}
async function showWorkspace() {authenticated=true;$('login').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;await state();clearInterval(poll);poll=setInterval(()=>state().catch(e=>notice(e.message)),2500);}
$('loginForm').onsubmit=async event=>{event.preventDefault();const button=event.target.querySelector('button');button.disabled=true;try{const result=await api('login',{password:$('password').value});csrf=result.csrf;$('password').value='';await showWorkspace();}catch(e){notice(e.message);}finally{button.disabled=false;}};
$('settingsForm').onsubmit=async event=>{
  event.preventDefault();
  try {
    await api('settings',{
      email:$('email').value,token:$('token').value,
      socks_host:$('socksHost').value,socks_port:$('socksPort').value,
      socks_user:$('socksUser').value,socks_password:$('socksPassword').value
    });
    $('token').value='';$('socksPassword').value='';
    notice('Настройки сохранены');await state();
  }catch(e){notice(e.message);}
};
$('createForm').onsubmit=async event=>{event.preventDefault();$('createButton').disabled=true;try{await api('create',{slug:$('slug').value});$('slug').value='';await state();}catch(e){notice(e.message);$('createButton').disabled=false;}};
$('logout').onclick=async()=>{try{await api('logout',{});}finally{showLogin();}};
(async()=>{try{const session=await api('session');csrf=session.csrf||'';if(session.authenticated)await showWorkspace();else showLogin();}catch(e){showLogin();notice('Панель временно недоступна. Обновите страницу.');}})();

function selectPage(name) {
  document.querySelectorAll('[data-page]').forEach(page=>page.hidden=page.dataset.page!==name);
  document.querySelectorAll('[data-tab]').forEach(button=>{
    if(button.dataset.tab===name)button.setAttribute('aria-current','page');
    else button.removeAttribute('aria-current');
  });
}
document.querySelectorAll('[data-tab]').forEach(button=>button.onclick=()=>selectPage(button.dataset.tab));
$('openSettings').onclick=()=>selectPage('settings');
