/* Automotive layout extends the shared library and playback controls. */
const carWords={
 'zh-Hant':['車機啟動與續播','啟動／ACC 喚醒後自動續播','續播等待秒數','由車機系統啟動 APP；電台重新連線，本機歌曲接續秒數。網路或檔案未就緒時會等待。','自動續播','等待連線','等待音樂檔／權限','等待上次曲目','準備續播','已恢復上次播放','尚無播放記錄','自動續播已關閉','喚醒後等待','網路','離線','休眠記憶已保存','請手動播放'],
 'zh-Hans':['车机启动与续播','启动／ACC 唤醒后自动续播','续播等待秒数','由车机系统启动 APP；电台重新连接，本机歌曲接续秒数。网络或文件未就绪时会等待。','自动续播','等待连接','等待音乐文件／权限','等待上次曲目','准备续播','已恢复上次播放','尚无播放记录','自动续播已关闭','唤醒后等待','网络','离线','休眠记忆已保存','请手动播放'],
 en:['Car start & resume','Resume after launch / ACC wake','Resume delay (seconds)','The head unit launches the app. Radio reconnects live; local tracks resume their position. Waits for network or storage.','Auto resume','Waiting for network','Waiting for music / access','Last item unavailable','Resuming playback','Last playback restored','No playback history','Auto resume off','Wake-up delay','Online','Offline','Sleep position saved','Tap to play'],
 ja:['車載・再生再開','起動／ACC 復帰時に再生再開','待機時間（秒）','車載機側でアプリを起動します。ラジオは再接続、音楽は前回の位置から再開。通信とファイルの準備を待ちます。','自動再開','接続待ち','音楽／権限待ち','前回の曲がありません','再生再開中','前回の再生を復元','再生履歴なし','自動再開オフ','復帰後の待機','オンライン','オフライン','再生位置を保存','タップして再生'],
 ko:['차량 시작 및 이어듣기','시작 / ACC 복귀 시 자동 재생','대기 시간 (초)','차량 시스템에서 앱을 시작합니다. 라디오는 재연결하고 음악은 저장된 위치부터 재생합니다. 네트워크와 파일을 기다립니다.','자동 재생','네트워크 대기','음악 / 권한 대기','이전 곡 없음','재생 시작 중','이전 재생 복원','재생 기록 없음','자동 재생 꺼짐','복귀 대기','온라인','오프라인','재생 위치 저장','눌러서 재생'],
 fr:['Démarrage et reprise','Reprendre au démarrage / réveil ACC','Délai (secondes)','Le système du véhicule lance l’application. La radio se reconnecte en direct ; la musique reprend sa position. Attend le réseau ou les fichiers.','Reprise auto','Attente du réseau','Attente des fichiers / accès','Dernier titre indisponible','Reprise en cours','Lecture restaurée','Aucun historique','Reprise auto désactivée','Délai au réveil','En ligne','Hors ligne','Position enregistrée','Touchez pour lire'],
 es:['Inicio y reanudación','Reanudar al iniciar / despertar ACC','Espera (segundos)','El sistema del vehículo inicia la app. La radio vuelve al directo; la música continúa desde su posición. Espera la red o los archivos.','Reanudar auto','Esperando red','Esperando música / acceso','Última pista no disponible','Reanudando','Reproducción restaurada','Sin historial','Reanudación desactivada','Espera al despertar','En línea','Sin conexión','Posición guardada','Toque para reproducir'],
 'pt-BR':['Inicialização e retomada','Retomar ao iniciar / despertar ACC','Espera (segundos)','O sistema do carro inicia o app. A rádio reconecta ao vivo; a música retoma a posição. Aguarda rede ou arquivos.','Retomar auto','Aguardando rede','Aguardando música / acesso','Última faixa indisponível','Retomando','Reprodução restaurada','Sem histórico','Retomada desativada','Espera ao despertar','Online','Offline','Posição salva','Toque para reproduzir'],
 de:['Start und Fortsetzen','Beim Start / ACC-Aufwachen fortsetzen','Wartezeit (Sekunden)','Das Fahrzeugsystem startet die App. Radio verbindet live; Musik setzt an der gespeicherten Stelle fort. Wartet auf Netzwerk oder Dateien.','Automatisch fortsetzen','Warten auf Netzwerk','Warten auf Musik / Zugriff','Letzter Titel fehlt','Wiedergabe wird fortgesetzt','Wiedergabe wiederhergestellt','Kein Verlauf','Automatik aus','Wartezeit beim Aufwachen','Online','Offline','Position gespeichert','Zum Abspielen tippen'],
 vi:['Khởi động và tiếp tục','Tự phát khi khởi động / đánh thức ACC','Chờ (giây)','Hệ thống xe khởi chạy ứng dụng. Radio kết nối trực tiếp; nhạc tiếp tục từ vị trí đã lưu. Chờ mạng hoặc tệp.','Tự tiếp tục','Đang chờ mạng','Chờ nhạc / quyền truy cập','Không có bài trước','Đang tiếp tục','Đã khôi phục phát nhạc','Chưa có lịch sử','Đã tắt tự tiếp tục','Chờ sau đánh thức','Trực tuyến','Ngoại tuyến','Đã lưu vị trí','Chạm để phát'],
 th:['เริ่มและเล่นต่อ','เล่นต่อเมื่อเริ่ม / ปลุก ACC','รอ (วินาที)','ระบบรถเปิดแอป วิทยุเชื่อมต่อสด เพลงเล่นต่อจากตำแหน่งเดิม รอเครือข่ายหรือไฟล์พร้อม','เล่นต่ออัตโนมัติ','รอเครือข่าย','รอเพลง / สิทธิ์','ไม่พบเพลงก่อนหน้า','กำลังเล่นต่อ','คืนค่าการเล่นแล้ว','ไม่มีประวัติ','ปิดเล่นต่ออัตโนมัติ','รอหลังปลุก','ออนไลน์','ออฟไลน์','บันทึกตำแหน่งแล้ว','แตะเพื่อเล่น'],
 id:['Mulai dan lanjutkan','Lanjut saat mulai / bangun ACC','Tunggu (detik)','Sistem mobil membuka aplikasi. Radio terhubung langsung; musik melanjutkan posisi tersimpan. Menunggu jaringan atau berkas.','Lanjut otomatis','Menunggu jaringan','Menunggu musik / akses','Lagu terakhir tidak tersedia','Melanjutkan','Pemutaran dipulihkan','Belum ada riwayat','Lanjut otomatis mati','Jeda setelah bangun','Daring','Luring','Posisi tersimpan','Ketuk untuk memutar']
};
function carText(n){return (carWords[locale]||carWords.en)[n];}
const carBasePage=renderPage,carBaseLists=renderLists,carBaseSelect=selectPlaylist,carBaseAppearance=applyAppearance,carBaseLanguage=applyLanguage;
renderPage=function(){carBasePage();const groups=page==='playlists';$('car-playlists').hidden=!groups;if(groups){$('library-list').hidden=true;$('btn-add').hidden=false;$('btn-select').hidden=true;$('playlist-picker').hidden=true;$('page-label').textContent=t('playlists');$('page-eyebrow').textContent='YOUR PERSONAL COLLECTION';$('list-count').textContent=state.playlists.length;$('btn-add').querySelector('span').textContent=t('newPlaylist');carRenderPlaylists();}scheduleReceiverFit();};
renderLists=function(){if(page==='playlists')carRenderPlaylists();else carBaseLists();};
selectPlaylist=function(id){if(page==='playlists')page='local';carBaseSelect(id);};
function carRenderPlaylists(){const container=$('car-playlists');container.replaceChildren();for(const group of state.playlists){const row=document.createElement('div');row.className='car-playlist-row';const open=document.createElement('button');open.className='car-playlist-open';open.append(icon('music'));const name=document.createElement('span');name.textContent=group.name;const count=document.createElement('small');count.textContent=S.playlistItems(state,group.id).length;open.append(name,count);open.onclick=()=>selectPlaylist(group.id);const more=document.createElement('button');more.className='icon-button';more.append(icon('more'));more.setAttribute('aria-label',t('edit'));more.onclick=()=>{state.selectedPlaylistId=group.id;openPlaylists();};row.append(open,more);container.append(row);}if(!state.playlists.length){const empty=document.createElement('button');empty.className='secondary-button';empty.textContent='＋ '+t('newPlaylist');empty.onclick=()=>editPlaylist();container.append(empty);}}
applyAppearance=function(){carBaseAppearance();carApplyListFont();$('car-auto').checked=state.carAutoResume;$('car-delay').value=String(state.carResumeDelaySec);carRefresh();};
applyLanguage=function(){carBaseLanguage();carFontLanguage();$('car-settings-title').textContent=carText(0);$('car-auto-label').textContent=carText(1);$('car-delay-label').textContent=carText(2);$('car-settings-note').textContent=carText(3);carRefresh();};
function carRefresh(){if(!state)return;const chip=$('car-auto-chip');chip.textContent=carText(4)+' '+(state.carAutoResume?'ON':'OFF');chip.classList.toggle('active',state.carAutoResume);$('car-clock').textContent=new Date().toLocaleTimeString(locale,{hour:'2-digit',minute:'2-digit'});let status={};try{status=JSON.parse(AMNative.carStatus());}catch{}const stages={network:5,media:6,missing:7,resuming:8,restored:9,active:9,empty:10,off:11,delay:12,sleep:15,blocked:16,manual:16,ready:16};$('car-network').textContent=status.online===false?carText(14):carText(13);$('car-resume-status').textContent=carText(stages[status.stage]??16)+(status.position>0?' · '+format(status.position/1000):'');}
window.addEventListener('DOMContentLoaded',()=>{
 $('deck-mode').textContent='CAR AUDIO';$('btn-expand-list').hidden=true;$('car-auto-chip').onclick=()=>showPage('settings');
 const favorite=$('btn-favorite');document.querySelector('.deck-bottom').append(favorite);
 const originalAdd=$('btn-add').onclick;$('btn-add').onclick=()=>page==='playlists'?editPlaylist():originalAdd();
 $('car-auto').onchange=e=>{state.carAutoResume=e.target.checked;persist(true);carRefresh();toast(t('changed'));};$('car-delay').onchange=e=>{state.carResumeDelaySec=Number(e.target.value);persist(true);toast(t('changed'));};
 $('open-settings').onclick=toggleSettings;
 carRefresh();setInterval(carRefresh,1000);scheduleReceiverFit();
});

const carFontLabels={
 'zh-Hant':['清單字體大小','即時調整電台、最愛、本機與歌單列表'],
 'zh-Hans':['列表字体大小','即时调整电台、收藏、本机与歌单列表'],
 en:['List text size','Applies to radio, favorites, local music and playlists'],
 ja:['リストの文字サイズ','ラジオ、お気に入り、音楽とプレイリストに適用'],
 ko:['목록 글자 크기','라디오, 즐겨찾기, 음악 및 재생목록에 적용'],
 fr:['Taille du texte des listes','Radio, favoris, musique locale et playlists'],
 es:['Texto de las listas','Radio, favoritos, música local y listas'],
 'pt-BR':['Texto das listas','Rádio, favoritos, música local e playlists'],
 de:['Schriftgröße der Listen','Radio, Favoriten, lokale Musik und Playlists'],
 vi:['Cỡ chữ danh sách','Radio, yêu thích, nhạc và danh sách phát'],
 th:['ขนาดข้อความรายการ','วิทยุ รายการโปรด เพลง และเพลย์ลิสต์'],
 id:['Ukuran teks daftar','Radio, favorit, musik lokal dan playlist']
};
function carFontLanguage(){const words=carFontLabels[locale]||carFontLabels.en;$('car-font-label').textContent=words[0];$('car-font-hint').textContent=words[1];$('car-list-font').setAttribute('aria-label',words[0]);}
function carApplyListFont(){if(!state)return;const size=state.carListFontSize;document.body.style.setProperty('--car-list-font-size',size+'px');$('car-list-font').value=size;$('car-font-value').value=size+' px';}
const carBaseAlign=alignLcdReadout;
alignLcdReadout=function(){
 const button=$('elapsed'),digits=button.querySelector('.time-digits');if(!button.offsetParent)return;
 const available=Math.max(1,button.clientWidth-2);digits.style.fontSize='inherit';
 const width=digits.getBoundingClientRect().width,scale=$('receiver').getBoundingClientRect().width/$('receiver').offsetWidth;
 const natural=width/Math.max(.01,scale),base=parseFloat(getComputedStyle(button).fontSize);
 if(natural>available)digits.style.fontSize=Math.max(8,base*available/natural)+'px';
 lcdAlignmentKey='';carBaseAlign();
};
window.addEventListener('DOMContentLoaded',()=>{
 $('car-list-font').oninput=e=>{state.carListFontSize=Number(e.target.value);carApplyListFont();persist();};
 $('car-list-font').onchange=()=>{persist(true);toast(t('changed'));};
 const observer=new ResizeObserver(scheduleLcdAlignment);observer.observe($('elapsed'));observer.observe(document.querySelector('.lcd-bottom'));
 new MutationObserver(scheduleLcdAlignment).observe($('elapsed').querySelector('.time-digits'),{childList:true,characterData:true,subtree:true});
 carApplyListFont();carFontLanguage();scheduleLcdAlignment();
});
