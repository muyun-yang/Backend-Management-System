<template>
  <div class="knowledge-base-container">
    <h2>知识库管理</h2>
    <div class="knowledge-base-panel">
      <div class="file-list-container">
        <h3>已上传文件</h3>
        <ul class="file-list">
          <li v-for="file in knowledgeFiles" :key="file.filedId" class="file-item">
            <span class="file-name">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                <path d="M14,2H6A2,2 0 0,0 4,4V20A2,2 0 0,0 6,22H18A2,2 0 0,0 20,20V8L14,2M13.5,14.5L12,17.72L10.5,14.5H13.5M20,8V20H6V4H13V9A1,1 0 0,0 14,10H19L20,8M15.5,13L16.25,11.25H18.75L19.5,13H15.5M16.5,10L14.5,15H19.5L17.5,10H16.5Z" />
              </svg>
              <a href="#" @click.prevent="showFileContent(file.filedId)">{{ file.fileName }}</a>
            </span>
            <button @click="deleteFile(file.filedId)" class="delete-button">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                <path d="M9,3V4H4V6H20V4H15V3A1,1 0 0,0 14,2H10A1,1 0 0,0 9,3M4,8V21A2,2 0 0,0 6,23H18A2,2 0 0,0 20,21V8H4M6,10H8V21H6V10M16,10H18V21H16V10M11,10H13V21H11V10Z" />
              </svg>
              删除
            </button>
          </li>
        </ul>
        <p v-if="knowledgeFiles.length === 0" class="empty-hint">还没有文件，快去上传吧！</p>
      </div>

      <div class="upload-panel">
        <div class="upload-area">
          <div class="upload-url-area">
            <input type="text" v-model="url" placeholder="在此输入文件下载链接">
            <button @click="handleUrlUpload" :disabled="urlUploading">
              <span v-if="urlUploading" class="spinner"></span>
              {{ urlUploading ? '正在上传...' : '通过 URL 上传' }}
            </button>
          </div>
          <label for="file-upload" class="upload-label">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path d="M19,13H15V19H13V13H9V11H13V7H15V11H19V13M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z" />
            </svg>
            选择文件
          </label>
          <input type="file" id="file-upload" @change="handleFileUpload">
          <span v-if="uploading" class="upload-status">
            <span class="spinner"></span>
            正在上传...
          </span>
        </div>
        <div class="text-upload-area">
          <h3>上传文章内容</h3>
          <textarea v-model="textContent" placeholder="在此粘贴文章内容"></textarea>
          <input type="text" v-model="textTitle" placeholder="请输入文章标题 (可选)">
          <button @click="handleTextUpload" :disabled="textUploading || !textContent">
            <span v-if="textUploading" class="spinner"></span>
            {{ textUploading ? '正在上传...' : '上传文章' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="fileContent" class="file-content-overlay" @click.self="closeContent">
      <div class="file-content-modal">
        <button class="close-button" @click="closeContent">X</button>
        <h3>{{ currentFileName }}</h3>
        <div class="content-body">{{ fileContent }}</div>
      </div>
    </div>
  </div>
</template>
<script>
import axios from 'axios';

export default {
  name: 'KnowledgeInterface',
  data() {
    return {
      knowledgeFiles: [],
      uploading: false,
      apiBaseUrl: '/api/knowledge',
      fileContent: '',
      currentFileName: '' ,
      url: '', // 新增用于绑定输入框的数据
      urlUploading: false, // 新增 URL 上传状态
      textContent: '',
      textTitle: '',
      textUploading: false
    };
  },
  created() {
    this.fetchFiles();
  },

  methods: {
    async fetchFiles() {
      try {
        const response = await axios.get(`${this.apiBaseUrl}/files`);
        this.knowledgeFiles = response.data;
      } catch (error) {
        console.error('获取文件列表失败:', error);
      }
    },

    async handleFileUpload(event) {
      const file = event.target.files[0];
      if (!file) return;

      this.uploading = true;
      const formData = new FormData();
      formData.append('file', file);

      try {
        await axios.post(`${this.apiBaseUrl}/upload`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        this.fetchFiles();
        console.log('文件上传成功');
      } catch (error) {
        console.error('文件上传失败:', error);
      } finally {
        this.uploading = false;
        event.target.value = '';
      }
    },

    async deleteFile(fileId) {
      if (!confirm('确定要删除这个文件吗？此操作不可撤销。')) return;

      try {
        await axios.delete(`${this.apiBaseUrl}/files`, {
          params: { fileName: fileId }
        });
        this.knowledgeFiles = this.knowledgeFiles.filter(f => f.filedId !== fileId);
        console.log('文件删除成功:', fileId);
      } catch (error) {
        console.error('文件删除失败:', error);
        alert('删除失败，请稍后重试。');
      }
    },

    async showFileContent(fileId) {
      try {
        // Find the file name from the local list
        const file = this.knowledgeFiles.find(f => f.filedId === fileId);
        if (file) {
          this.currentFileName = file.fileName;
        }

        const response = await axios.get(`${this.apiBaseUrl}/files/${fileId}/content`);
        this.fileContent = response.data; // Store content in data property
      } catch (error) {
        console.error("获取文件内容失败:", error);
        this.fileContent = '无法读取文件内容。';
      }
    },

    closeContent() {
      this.fileContent = '';
      this.currentFileName = '';
    },

    async handleUrlUpload() {
      if (!this.url) return;
      this.urlUploading = true;

      try {
        await axios.post(`${this.apiBaseUrl}/upload-url`, { url: this.url });
        this.fetchFiles(); // 重新获取文件列表
        this.url = ''; // 清空输入框
        console.log('URL 文件上传成功');
      } catch (error) {
        console.error('URL 文件上传失败:', error);
      } finally {
        this.urlUploading = false;
      }
    },

    async handleTextUpload() {
      if (!this.textContent) {
        alert("内容不能为空");
        return;
      }
      this.textUploading = true;
      try {
        const payload = {
          content: this.textContent,
          title: this.textTitle
        };
        await axios.post(`${this.apiBaseUrl}/upload-text`, payload);
        this.fetchFiles(); // Refresh file list
        this.textContent = ''; // Clear the textarea
        this.textTitle = '';   // Clear the title input
        console.log('文本内容上传成功');
      } catch (error) {
        console.error('文本内容上传失败:', error);
        alert('文本上传失败，请稍后重试。');
      } finally {
        this.textUploading = false;
      }
    },
  }
};
</script>

<style scoped>
@import "../assets/knowledge.css";


</style>