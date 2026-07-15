<template>
  <div class="chat-app">
    <aside class="sidebar">
      <button @click="createNewSession" class="new-chat-btn">+ 新对话</button>
      <div class="session-list">
        <div 
          v-for="s in chatSessions" :key="s.id" 
          :class="['session-item', { active: currentSessionId === s.id }]"
          @click="switchSession(s.id)"
        >
          <span>{{ s.title }}</span>
        <i class="delete-icon" @click.stop="handleDelete(s.id)">×</i>
      </div>
      </div>
    </aside>

    <main class="chat-main">
      <div class="message-list" ref="messageList">
        <div v-for="msg in messages" :key="msg.id" :class="['msg-wrapper', msg.sender]">
          <div class="msg-bubble">
            <img v-if="msg.type === 'image'" :src="msg.url" class="chat-image" />
            <div v-else v-html="renderMarkdown(msg.text)" class="markdown-body"></div>
          </div>
        </div>
        <div v-if="isSending" class="msg-wrapper ai">
          <div class="msg-bubble loading-container">
            <div class="typing-dot" style="--i: 1"></div>
            <div class="typing-dot" style="--i: 2"></div>
            <div class="typing-dot" style="--i: 3"></div>
            <span class="loading-text">AI 正在分析表格...</span>
          </div>
        </div>
      </div>

      <footer class="input-area">
        <div class="input-container">
          <div v-if="pendingImage" class="pending-wrapper">
            <img :src="pendingImage" class="preview-thumbnail" />
            <button @click="removePendingImage" class="remove-btn" title="移除图片">×</button>
          </div>

          <div class="input-row">
            <input type="file" ref="fileRef" hidden @change="handleFileSelect" accept="image/*" />
            
            <button class="icon-btn" @click="$refs.fileRef.click()" title="上传图片表格">📎</button>
            
            <textarea 
              v-model="inputText" 
              placeholder="询问关于表格的内容..." 
              @keyup.enter.exact.prevent="sendAll"
              rows="1"
            ></textarea>
            
            <button 
              class="send-btn" 
              @click="sendAll" 
              :disabled="isSending || (!inputText.trim() && !pendingFile)"
            >发送</button>
          </div>
        </div>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';
import { v4 as uuidv4 } from 'uuid';
import MarkdownIt from 'markdown-it';
import axios from 'axios';


const pendingImage = ref(null); // 存储图片的 Base64 用于预览
const pendingFile = ref(null);  // 存储真正的 File 对象用于上传
const userInput = ref("");      // 绑定输入框的文字

const md = new MarkdownIt({
  html: true,        
  linkify: true,     
  typographer: true, 
  breaks: true       
});
const messages = ref([]);
const inputText = ref('');
const isSending = ref(false);
const chatSessions = ref(JSON.parse(localStorage.getItem('sessions') || '[]'));
const currentSessionId = ref(null);
const messageList = ref(null);

// 渲染 Markdown
const renderMarkdown = (text) => md.render(text || '');

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight;
  });
};

// 发送文本
const sendText = async () => {
  if (!inputText.value.trim() || isSending.value) return;
  
  const userText = inputText.value;
  pushMessage('user', 'text', userText);
  inputText.value = '';
  
  await callAiApi(userText);
};


const handleFileSelect = (e) => {
  const file = e.target.files[0];
  if (!file) return;

  pendingFile.value = file;

  const reader = new FileReader();
  reader.onload = (event) => {
    pendingImage.value = event.target.result; // 用于预览
  };
  reader.readAsDataURL(file);
  e.target.value = ''; 
};

const removePendingImage = () => {
  pendingImage.value = null;
  pendingFile.value = null;
};

// 3. 统一发送逻辑
const sendAll = async () => {
  if (isSending.value) return;
  if (!inputText.value.trim() && !pendingFile.value) return;

  const textToSend = inputText.value;
  const fileToSend = pendingFile.value;
  const imagePreview = pendingImage.value;

  // 1. 立即更新 UI 状态
  inputText.value = '';
  removePendingImage();
  if (imagePreview) pushMessage('user', 'image', null, imagePreview);
  if (textToSend) pushMessage('user', 'text', textToSend);

  isSending.value = true;

  // 2. 准备 AI 的占位消息（用于逐字填充内容）
  const aiMsgId = uuidv4();
  messages.value.push({ 
    id: aiMsgId, 
    sender: 'ai', 
    type: 'text', 
    text: '' 
  });

  try {
    const formData = new FormData();
    if (textToSend) formData.append('prompt', textToSend);
    
    let url = `/api/chat/sessions/${currentSessionId.value}/messages`;
    
    // 如果有文件，走图片接口；否则走普通接口
    if (fileToSend) {
      formData.append('file', fileToSend);
      url += '/image';
    }

    // 3. 使用 fetch 发起流式请求
    const response = await fetch(url, {
      method: 'POST',
      body: fileToSend ? formData : JSON.stringify({ prompt: textToSend }),
      headers: fileToSend ? {} : { 'Content-Type': 'application/json' }
    });

    if (!response.ok) throw new Error('网络响应异常');

    // 4. 读取流数据
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = ''; // 增加缓冲区处理粘包
    
    // --- 修改后的流处理逻辑 ---
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      
      // SSE 的标准格式是 data: 内容\n\n
      let lines = buffer.split('\n');
      buffer = lines.pop(); // 保持缓冲区完整性

      for (let line of lines) {
        const trimmedLine = line.trim();
        if (!trimmedLine) continue;

        // 1. 处理 Spring AI 标准的 data: 前缀
        if (trimmedLine.startsWith('data:')) {
          const content = trimmedLine.substring(5).trim();
          
          // 特别注意：Spring AI 结束时可能会发送 [DONE]
          if (content === '[DONE]') continue;

          const aiMsg = messages.value.find(m => m.id === aiMsgId);
          if (aiMsg) {
            aiMsg.text += content;
            scrollToBottom();
          }
        } 
        // 2. 捕获我们在 Controller 中自定义的 error 事件
        else if (trimmedLine.startsWith('event:error')) {
          // 如果后端发了 event:error，下一行通常是 data: 错误信息
          // 这里简单处理：如果发现错误标识，可以让后续文字变色或提示
          console.error("检测到后端错误事件");
        }
        // 3. 兜底逻辑：如果后端没加 data: 前缀直接发的字符串
        else {
          const aiMsg = messages.value.find(m => m.id === aiMsgId);
          if (aiMsg) {
            aiMsg.text += trimmedLine;
            scrollToBottom();
          }
        }
      }
    }
  } catch (err) {
    console.error("发送失败:", err);
    const aiMsg = messages.value.find(m => m.id === aiMsgId);
    if (aiMsg) aiMsg.text = '❌ 发送失败：' + err.message;
  } finally {
    isSending.value = false;
    saveSessions();
  }
};

const handleDelete = (id) => {
  // window.confirm 会返回 true (确定) 或 false (取消)
  const isConfirmed = window.confirm("确定要删除这段对话吗？删除后将无法找回。");
  
  // 如果用户点击了“取消”，则直接结束函数，不执行后面的删除逻辑
  if (!isConfirmed) return;

  // 1. 找到对应的索引
  const index = chatSessions.value.findIndex(s => s.id === id);
  if (index === -1) return;

  // 2. 从响应式数组中移除 (Vue 3 会自动侦测 splice)
  chatSessions.value.splice(index, 1);

  // 3. 持久化到本地
  localStorage.setItem('sessions', JSON.stringify(chatSessions.value));

  // 4. 如果删掉的是当前激活的对话，需要跳转
  if (currentSessionId.value === id) {
    if (chatSessions.value.length > 0) {
      switchSession(chatSessions.value[0].id);
    } else {
      createNewSession(); // 如果全删光了，新建一个
    }
  }
};

const callAiApi = async (prompt) => {
  isSending.value = true;
  try {
    const res = await axios.post(`/api/chat/sessions/${currentSessionId.value}/messages`, { prompt });
    pushMessage('ai', 'text', res.data);
  } catch (err) {
    pushMessage('ai', 'text', '发生错误：' + err.message);
  } finally {
    isSending.value = false;
  }
};

const pushMessage = (sender, type, text, url = null) => {
  messages.value.push({ id: uuidv4(), sender, type, text, url });
  saveSessions();
  scrollToBottom();
};

// 会话管理
const createNewSession = () => {
  const newSession = { id: uuidv4(), title: '新对话 ' + (chatSessions.value.length + 1), messages: [] };
  chatSessions.value.unshift(newSession);
  switchSession(newSession.id);
};

const switchSession = (id) => {
  currentSessionId.value = id;
  const session = chatSessions.value.find(s => s.id === id);
  messages.value = session ? session.messages : [];
  scrollToBottom();
};

const saveSessions = () => {
  const session = chatSessions.value.find(s => s.id === currentSessionId.value);
  if (session) session.messages = messages.value;
  localStorage.setItem('sessions', JSON.stringify(chatSessions.value));
};

onMounted(() => {
  if (chatSessions.value.length === 0) createNewSession();
  else switchSession(chatSessions.value[0].id);
});
</script>

<style scoped>
/* 极简核心布局 */
.chat-app {
  display: flex;
  height: 100vh;
  background: #f7f7f8;
  font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  color: #37352f;
}
.sidebar {
  width: 260px;
  background: #202123;
  color: #ececf1;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.new-chat-btn {
  border: 1px solid #4d4d4f;
  background: transparent;
  color: white;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  font-weight: 500;
  margin-bottom: 20px;
}
.new-chat-btn:hover { background: #2b2c2f; }

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
}

.msg-wrapper {
  display: flex;
  padding: 24px 15%; /* 消息列表在宽屏时居中对齐的关键 */
  transition: background 0.1s;
}


.msg-wrapper.user { background: transparent; }
.msg-wrapper.ai { background: #f9f9f9; border-top: 1px solid #eee; border-bottom: 1px solid #eee; }

.msg-bubble {
  max-width: 850px;
  width: 100%;
  font-size: 15px; /* 统一文字大小 */
  line-height: 1.65;
  color: #37352f;
}

.markdown-body :deep(p) {
  margin: 0 0 12px 0;
}

/* 🖼️ 表格深度美化 */
.markdown-body :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  border-collapse: collapse;
  border-spacing: 0;
  margin: 16px 0;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  overflow-x: auto;
  background: white;
  transition: all 0.2s ease;
  word-break: break-word;
}



.markdown-body :deep(th) {
  background: #f8f9fa;
  font-weight: 600;
  border: 1px solid #e0e0e0;
  padding: 10px 14px;
  border-bottom: 2px solid #eee;
  text-align: left;
  white-space: nowrap;
}

.markdown-body :deep(td) {
  border: 1px solid #e0e0e0;
  padding: 8px 14px;
  vertical-align: middle;
}
.markdown-body :deep(tr:nth-child(even)) {
  background-color: #fafbfc;
}
.markdown-body :deep(tr:last-child td) { border-bottom: none; }
.markdown-body :deep(tr:hover) {

  background-color: #f1f3f5;
}
.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 20px;
  margin-bottom: 12px;
}

/* 输入框区域：浮动质感 */
.input-area {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 24px 15%;
  background: linear-gradient(180deg, rgba(247,247,248,0), #f7f7f8 40%);
}

.input-container {
  display: flex;
  flex-direction: column; /* 改为纵向，预览图在文字上方 */
  background: white;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  padding: 8px 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

/* 预览图包装层 */
.pending-wrapper {
  position: relative;
  display: inline-block;
  width: 60px;
  height: 60px;
  margin: 0 0 10px 8px;
}

.preview-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #ddd;
}

.remove-btn {
  position: absolute;
  top: -5px;
  right: -5px;
  background: rgba(0,0,0,0.5);
  color: white;
  border: none;
  border-radius: 50%;
  width: 16px;
  height: 16px;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-btn:hover { background: #ff4d4f; }

/* 输入行样式 */
.input-row {
  display: flex;
  align-items: center;
  width: 100%;
}

.icon-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  padding: 0 10px;
  color: #666;
}

/* 确保消息列表中的图片不要太大 */
.chat-image {
  max-width: 300px;
  border-radius: 8px;
  margin: 5px 0;
}


.loading-container {
  display: flex;
  align-items: center;
  gap: 5px;
  background: #f0f0f0 !important;
  color: #666;
}

.typing-dot {
  width: 6px;
  height: 6px;
  background: #999;
  border-radius: 50%;
  /* 使用简单的 bounce 动画 */
  animation: bounce 1.4s infinite ease-in-out;
  /* 根据变量 --i 计算延迟：第一个点不延迟，后面的点依次延迟 0.2s */
  animation-delay: calc(var(--i) * 0.2s);
}

/* 简单的跳动动画 */
.typing-dot:nth-child(1) { animation: bounce 1.2s infinite 0.1s; }
.typing-dot:nth-child(2) { animation: bounce 1.2s infinite 0.2s; }
.typing-dot:nth-child(3) { animation: bounce 1.2s infinite 0.3s; }

@keyframes bounce {
  0%, 80%, 100% { 
    transform: translateY(0); 
    opacity: 0.4;
  }
  40% { 
    transform: translateY(-6px); 
    opacity: 1;
  }
}

.loading-text {
  margin-left: 10px;
  font-size: 13px;
  font-weight: 500;
}

textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 16px;
  max-height: 200px;
}

.send-btn {
  background: #10a37f; /* ChatGPT 绿色 */
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 6px;
  font-weight: 500;
  margin-left: 8px;
}
.send-btn:disabled { background: #d9d9e3; }

.session-item {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  cursor: pointer;
}

.delete-icon {
  display: none; /* 默认隐藏 */
  padding: 2px 6px;
  border-radius: 4px;
  color: #888;
  transition: all 0.2s;
}

.session-item:hover .delete-icon {
  display: block; /* 悬停时显示，防止误删 */
}

.delete-icon:hover {
  background: #444;
  color: #ff4d4f;
}

</style>