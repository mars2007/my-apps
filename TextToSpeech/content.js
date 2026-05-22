// content.js
(function () {
  const $$ = (s, root=document) => [...root.querySelectorAll(s)];

  // ===== 音声ロード待ち =====
  function waitForVoices(timeoutMs = 2000) {
    return new Promise((resolve) => {
      const v = speechSynthesis.getVoices();
      if (v && v.length) return resolve(v);
      const t = setTimeout(() => resolve(speechSynthesis.getVoices()), timeoutMs);
      speechSynthesis.onvoiceschanged = () => { clearTimeout(t); resolve(speechSynthesis.getVoices()); };
    });
  }

  // ===== rubyポリシー =====
  // policy: "prefer_rt" | "ignore_rt" | "as_is"
  function extractTextWithRubyPolicyFromNode(node, policy) {
    const clone = node.cloneNode(true);
    if (policy === "prefer_rt") {
      clone.querySelectorAll("ruby").forEach(ruby => {
        const rtText = [...ruby.querySelectorAll("rt")].map(n => n.innerText || n.textContent || "").join("");
        ruby.replaceWith(document.createTextNode(rtText));
      });
      clone.querySelectorAll("rp").forEach(n => n.remove());
    } else if (policy === "ignore_rt") {
      clone.querySelectorAll("rt, rp").forEach(n => n.remove());
      clone.querySelectorAll("ruby").forEach(ruby => ruby.replaceWith(...ruby.childNodes));
    }
    return (clone.innerText || clone.textContent || "")
      .replace(/\u00A0/g, " ")
      .replace(/\s+\n/g, "\n").replace(/\n{3,}/g, "\n\n")
      .trim();
  }
  function getSelectionTextWithPolicy(policy) {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return "";
    const frag = sel.getRangeAt(0).cloneContents();
    const div = document.createElement("div");
    div.appendChild(frag);
    return extractTextWithRubyPolicyFromNode(div, policy);
  }
  function getWholePageTextWithPolicy(policy) {
    const container = document.querySelector("article") || document.querySelector("main") || document.body;
    return extractTextWithRubyPolicyFromNode(container, policy);
  }

  function makeChunks(text, maxLen = 220) {
    const s = (text || "").replace(/\s+/g, " ").trim();
    if (!s) return [];
    const re = /([^。．！？!?]+[。．！？!?」』]*)/g;
    const out = []; let m;
    while ((m = re.exec(s))) out.push(m[0]);
    if (!out.length) out.push(s);
    return out.flatMap(p => (p.length <= maxLen) ? [p] : p.match(new RegExp(`.{1,${maxLen}}`, "g")));
  }

  async function chooseVoiceByNameOrJa(name) {
    const voices = await waitForVoices();
    let v = null;
    if (name) v = voices.find(x => x.name === name) || null;
    if (!v) v = voices.find(x => (x.lang||"").toLowerCase().startsWith("ja")) || voices[0] || null;
    return v;
  }

  async function speak(text, opts) {
    speechSynthesis.cancel();
    const v = await chooseVoiceByNameOrJa(opts?.voiceName || null);
    const rate  = parseFloat(opts?.rate ?? 1.0) || 1.0;
    const pitch = parseFloat(opts?.pitch ?? 0.0) || 0.0;
    const chunks = makeChunks(text, 220);
    if (!chunks.length) { alert("読み上げ対象テキストが空です。"); return; }
    chunks.forEach((txt) => {
      const u = new SpeechSynthesisUtterance(txt);
      if (v) u.voice = v;
      u.lang = v?.lang || "ja-JP";
      u.rate = rate;
      u.pitch = pitch;
      speechSynthesis.speak(u);
    });
  }

  function pauseResume(){ if (speechSynthesis.speaking && !speechSynthesis.paused) speechSynthesis.pause(); else if (speechSynthesis.paused) speechSynthesis.resume(); }
  function stop(){ speechSynthesis.cancel(); }

  // ===== パネル（ページ内フロート・ドラッグ移動）=====
  let floatPanel = null;
  function buildFloatPanel() {
    if (floatPanel) return floatPanel;
    const style = document.createElement("style");
    style.textContent = `
      #ttsFloatPanel{position:fixed;top:16px;right:16px;z-index:2147483647;background:#fff;
        border:1px solid #d9e1ee;border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,.18);
        font:13px/1.4 system-ui,sans-serif;color:#111; width:320px;}
      #ttsFloatPanel *{box-sizing:border-box}
      #ttsFloatHeader{cursor:move;display:flex;align-items:center;gap:8px;justify-content:space-between;
        padding:8px 10px;border-bottom:1px solid #eef2f7;background:#f8fbff;border-radius:12px 12px 0 0;}
      #ttsFloatHeader b{font-size:12px}
      #ttsFloatBody{padding:10px}
      #ttsFloatBody .row{display:flex;gap:8px;align-items:center}
      #ttsFloatBody label{display:block;margin-top:6px;color:#555}
      #ttsFloatBody input, #ttsFloatBody select, #ttsFloatBody button{
        width:100%;padding:6px 8px;border:1px solid #cfd9e6;border-radius:8px;background:#fff;font-size:13px}
      #ttsFloatBody button{cursor:pointer}
      #ttsCloseBtn{border:1px solid #cfd9e6;border-radius:8px;background:#fff;padding:2px 8px;cursor:pointer}
    `;
    document.documentElement.appendChild(style);

    const wrap = document.createElement("div");
    wrap.id = "ttsFloatPanel";
    wrap.innerHTML = `
      <div id="ttsFloatHeader"><b>読み上げコントロール</b>
        <button id="ttsCloseBtn" title="閉じる">×</button>
      </div>
      <div id="ttsFloatBody">
        <label>音声</label>
        <div class="row" style="gap:6px">
          <select id="fpVoice"><option value="">読み込み中…</option></select>
          <button id="fpReload" title="再読込" style="flex:0 0 auto;width:auto;padding:6px 10px">⟳</button>
        </div>
        <div class="row" style="margin-top:6px">
          <div><label>速度</label><input id="fpRate" type="number" min="0.25" max="4" step="0.1" value="1.0"></div>
          <div><label>ピッチ</label><input id="fpPitch" type="number" min="-20" max="20" step="0.1" value="0"></div>
        </div>
        <label style="display:flex;align-items:center;gap:8px;margin-top:6px">
          <input type="checkbox" id="fpPrefer" checked> ふりがなを優先する（rtのみ）
        </label>
        <label style="display:flex;align-items:center;gap:8px">
          <input type="checkbox" id="fpIgnore"> ふりがなを無視する（rbのみ）
        </label>
        <div class="row" style="margin-top:6px">
          <button id="fpSel">▶ 選択を読む</button>
          <button id="fpWhole">▶ 全体を読む</button>
        </div>
        <div class="row" style="margin-top:6px">
          <button id="fpPause">⏸ 一時停止/再開</button>
          <button id="fpStop">■ 停止</button>
        </div>
      </div>
    `;
    document.body.appendChild(wrap);

    // 音声リスト
    async function fillVoices() {
      const sel = wrap.querySelector("#fpVoice");
      sel.innerHTML = `<option value="">読み込み中…</option>`;
      const voices = await waitForVoices();
      sel.innerHTML = "";
      voices
        .sort((a,b)=>{
          const aj=(a.lang||"").toLowerCase().startsWith("ja");
          const bj=(b.lang||"").toLowerCase().startsWith("ja");
          if(aj!==bj) return aj?-1:1;
          return (a.name||"").localeCompare(b.name||"");
        })
        .forEach(v=>{
          const opt=document.createElement("option");
          opt.value=v.name; opt.textContent=`${v.name} - ${v.lang}`;
          sel.appendChild(opt);
        });
      // 保存があれば復元
      const saved = await chrome.storage.local.get(["voiceName","rate","pitch","rubyPolicy"]);
      if (saved.voiceName && [...sel.options].some(o=>o.value===saved.voiceName)) sel.value = saved.voiceName;
      if (saved.rate)  wrap.querySelector("#fpRate").value  = saved.rate;
      if (saved.pitch) wrap.querySelector("#fpPitch").value = saved.pitch;
      const policy = saved.rubyPolicy || "prefer_rt";
      wrap.querySelector("#fpPrefer").checked = policy === "prefer_rt";
      wrap.querySelector("#fpIgnore").checked = policy === "ignore_rt";
    }
    fillVoices();
    wrap.querySelector("#fpReload").addEventListener("click", fillVoices);

    // ふりがなトグルは排他
    const prefer = wrap.querySelector("#fpPrefer");
    const ignore = wrap.querySelector("#fpIgnore");
    function syncRuby(who){
      if (who===prefer && prefer.checked) ignore.checked=false;
      if (who===ignore && ignore.checked) prefer.checked=false;
      const rubyPolicy = prefer.checked ? "prefer_rt" : ignore.checked ? "ignore_rt" : "as_is";
      chrome.storage.local.set({ rubyPolicy });
    }
    prefer.addEventListener("change", ()=>syncRuby(prefer));
    ignore.addEventListener("change", ()=>syncRuby(ignore));

    // ボタン
    wrap.querySelector("#fpSel").addEventListener("click", async ()=>{
      const policy = prefer.checked ? "prefer_rt" : ignore.checked ? "ignore_rt" : "as_is";
      const text = getSelectionTextWithPolicy(policy);
      if (!text) { alert("選択テキストが空です。"); return; }
      await chrome.storage.local.set({
        voiceName: wrap.querySelector("#fpVoice").value,
        rate: wrap.querySelector("#fpRate").value,
        pitch: wrap.querySelector("#fpPitch").value,
        rubyPolicy: policy
      });
      speak(text, { voiceName: wrap.querySelector("#fpVoice").value, rate: wrap.querySelector("#fpRate").value, pitch: wrap.querySelector("#fpPitch").value });
    });
    wrap.querySelector("#fpWhole").addEventListener("click", async ()=>{
      const policy = prefer.checked ? "prefer_rt" : ignore.checked ? "ignore_rt" : "as_is";
      const text = getWholePageTextWithPolicy(policy);
      if (!text) { alert("ページのテキストが見つかりません。"); return; }
      await chrome.storage.local.set({
        voiceName: wrap.querySelector("#fpVoice").value,
        rate: wrap.querySelector("#fpRate").value,
        pitch: wrap.querySelector("#fpPitch").value,
        rubyPolicy: policy
      });
      speak(text, { voiceName: wrap.querySelector("#fpVoice").value, rate: wrap.querySelector("#fpRate").value, pitch: wrap.querySelector("#fpPitch").value });
    });
    wrap.querySelector("#fpPause").addEventListener("click", ()=>pauseResume());
    wrap.querySelector("#fpStop").addEventListener("click", ()=>stop());
    wrap.querySelector("#ttsCloseBtn").addEventListener("click", ()=>{ stop(); wrap.remove(); floatPanel=null; });

    // ドラッグ移動
    (function makeDraggable() {
      const header = wrap.querySelector("#ttsFloatHeader");
      let startX=0, startY=0, startLeft=0, startTop=0, dragging=false;
      header.addEventListener("mousedown", (e)=>{
        dragging=true;
        startX = e.clientX; startY = e.clientY;
        const rect = wrap.getBoundingClientRect();
        startLeft = rect.left; startTop = rect.top;
        document.addEventListener("mousemove", onMove);
        document.addEventListener("mouseup", onUp, { once:true });
        e.preventDefault();
      });
      function onMove(e){
        if(!dragging) return;
        const dx = e.clientX - startX;
        const dy = e.clientY - startY;
        wrap.style.left = `${Math.max(8, Math.min(window.innerWidth - wrap.offsetWidth - 8, startLeft + dx))}px`;
        wrap.style.top  = `${Math.max(8, Math.min(window.innerHeight - wrap.offsetHeight - 8, startTop + dy))}px`;
        wrap.style.right = "auto"; // 右固定解除
      }
      function onUp(){ dragging=false; document.removeEventListener("mousemove", onMove); }
    })();

    return (floatPanel = wrap);
  }

  function toggleFloatPanel() {
    if (floatPanel) { floatPanel.remove(); floatPanel = null; return; }
    buildFloatPanel();
  }

  // ===== メッセージ受信 =====
  chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
    if (!msg || !msg.type) return;

    if (msg.type === "GET_VOICES") {
      (async () => {
        const voices = await waitForVoices();
        const sorted = [...voices].sort((a,b)=>{
          const aj = (a.lang||"").toLowerCase().startsWith("ja");
          const bj = (b.lang||"").toLowerCase().startsWith("ja");
          if (aj!==bj) return aj?-1:1;
          return (a.name||"").localeCompare(b.name||"");
        });
        sendResponse({ voices: sorted.map(v => ({ name:v.name, lang:v.lang })) });
      })();
      return true;
    }

    if (msg.type === "READ_SELECTION") {
      const policy = msg.opts?.rubyPolicy || "prefer_rt"; // 既定を prefer_rt
      const text = getSelectionTextWithPolicy(policy);
      if (!text) { alert("選択テキストが空です。ページでテキストをドラッグ選択してください。"); return; }
      speak(text, msg.opts); sendResponse?.({ ok:true }); return;
    }

    if (msg.type === "READ_WHOLE") {
      const policy = msg.opts?.rubyPolicy || "prefer_rt";
      const text = getWholePageTextWithPolicy(policy);
      if (!text) { alert("ページのテキストが見つかりませんでした。"); return; }
      speak(text, msg.opts); sendResponse?.({ ok:true }); return;
    }

    if (msg.type === "PAUSE_RESUME") { pauseResume(); sendResponse?.({ok:true}); return; }
    if (msg.type === "STOP")         { stop();        sendResponse?.({ok:true}); return; }
    if (msg.type === "TOGGLE_FLOAT_PANEL") { toggleFloatPanel(); sendResponse?.({ok:true}); return; }
  });

  console.debug("[TTS] content.js ready (ruby default=prefer_rt, float panel enabled):", location.href);
  window.addEventListener("beforeunload", () => speechSynthesis.cancel());
})();
