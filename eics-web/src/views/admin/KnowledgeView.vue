<template>
  <div class="kb-layout">
    <!-- 左侧分类栏 -->
    <div class="kb-sidebar">
      <div class="cat-title">文档分类</div>
      <div :class="['cat-item', { active: categoryFilter === '' }]" @click="categoryFilter = ''; onSearch()">
        📚 全部 ({{ total }})
      </div>
      <div v-for="c in categories" :key="c" :class="['cat-item', { active: categoryFilter === c }]"
           @click="categoryFilter = c; onSearch()">
        {{ c }}
      </div>
    </div>

    <!-- 右侧文档列表 -->
    <div class="kb-main">
      <div class="panel">
        <div class="panel-header">文档列表</div>
        <div class="toolbar">
          <SearchBar placeholder="搜索文档标题..." filter-placeholder="全部状态"
            :filters="statusFilters" v-model="keyword" v-model:filter="statusFilter"
            @search="(p) => { keyword = p.keyword; statusFilter = p.filter; onSearch() }" />
          <el-button type="primary" :loading="uploading" @click="showUploadDialog = true">📎 上传文档</el-button>
        </div>

        <DataTable :columns="columns" :data="docs" :loading="loading"
          :total="total" :page-size="pageSize" :current-page="page"
          empty-text="暂无文档" @page-change="onPageChange">
          <template #col-title="{ row }">
            <span class="doc-link" @click="previewDoc = row; showPreview = true">{{ row.title }}</span>
          </template>
          <template #col-fileType="{ row }">
            <StatusTag :status="row.fileType?.toUpperCase()" :label="row.fileType" />
          </template>
          <template #col-category="{ row }">
            <el-select :model-value="row.category || ''" size="small" clearable placeholder="未分类"
                       style="width:100px" @change="(v) => handleUpdateMeta(row, 'category', v)">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
          </template>
          <template #col-status="{ row }">
            <StatusTag :status="row.status" />
          </template>
          <template #col-fileSize="{ row }">{{ formatSize(row.fileSize) }}</template>
          <template #actions="{ row }">
            <el-popconfirm title="确定删除此文档？" @confirm="handleDelete(row)">
              <template #reference><el-button type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </DataTable>
      </div>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="420px">
      <el-form label-width="60px">
        <el-form-item label="文件" required>
          <input ref="fileInput" type="file" accept=".pdf,.docx,.doc,.txt,.md" @change="onFileSelect" style="width:100%" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="uploadCategory" clearable placeholder="选择分类" style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="uploadTags" placeholder="逗号分隔，如：年假,考勤" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 文档预览弹窗 -->
    <el-dialog v-model="showPreview" :title="previewDoc?.title" width="700px" @close="previewDoc = null">
      <div v-if="previewDoc" style="max-height:60vh;overflow-y:auto">
        <p><strong>分类：</strong>{{ previewDoc.category || '未分类' }}</p>
        <p><strong>标签：</strong>{{ previewDoc.tags || '无' }}</p>
        <p><strong>类型：</strong>{{ previewDoc.fileType }} · {{ formatSize(previewDoc.fileSize) }} · {{ previewDoc.chunkCount }} 切片</p>
        <p><strong>状态：</strong><StatusTag :status="previewDoc.status" /></p>
        <p><strong>上传时间：</strong>{{ previewDoc.createTime }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocList, uploadDoc, deleteDoc } from '../../api'
import api from '../../api'
import SearchBar from '../../components/SearchBar.vue'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import { ElMessage } from 'element-plus'

const CATEGORIES = ['人事制度', 'IT支持', '产品文档', '培训资料', '其他']
const loading = ref(false)
const uploading = ref(false)
const docs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const statusFilter = ref('')
const categoryFilter = ref('')
const previewDoc = ref(null)
const showPreview = ref(false)
const showUploadDialog = ref(false)
const selectedFile = ref(null)
const uploadCategory = ref('')
const uploadTags = ref('')
const categories = ref(CATEGORIES)

const statusFilters = [
  { label: '就绪', value: 'READY' },
  { label: '解析中', value: 'PARSING' },
  { label: '失败', value: 'FAILED' }
]

const columns = [
  { prop: 'title', label: '标题', minWidth: '180' },
  { prop: 'category', label: '分类', width: '90' },
  { prop: 'fileType', label: '类型', width: '70' },
  { prop: 'fileSize', label: '大小', width: '80' },
  { prop: 'chunkCount', label: '切片', width: '60' },
  { prop: 'status', label: '状态', width: '80' },
  { prop: 'createTime', label: '上传时间', minWidth: '130' }
]

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024*1024) return (bytes/1024).toFixed(1) + ' KB'
  return (bytes/(1024*1024)).toFixed(1) + ' MB'
}

async function load() {
  loading.value = true
  try {
    const res = await getDocList(page.value, pageSize.value, statusFilter.value || undefined, keyword.value || undefined, categoryFilter.value || undefined)
    docs.value = res.data?.data?.records || []
    total.value = res.data?.data?.total || 0
  } catch (e) { ElMessage.error('加载失败') }
  loading.value = false
}

function onSearch() { page.value = 1; load() }
function onPageChange(p) { page.value = p; load() }

function onFileSelect(e) { selectedFile.value = e.target.files[0] }

async function handleUpload() {
  const file = selectedFile.value
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadDoc(file)
    if (res.code === 200) {
      ElMessage.success(`「${file.name}」上传成功`)
      const docId = res.data?.id
      const cat = uploadCategory.value; const tag = uploadTags.value
      showUploadDialog.value = false
      selectedFile.value = null; uploadCategory.value = ''; uploadTags.value = ''
      // 更新分类和标签（非关键，失败不影响上传）
      if (docId && (cat || tag)) {
        try { await api.put(`/doc/${docId}/meta`, { category: cat, tags: tag }) } catch (_) {}
      }
      load()
    } else { ElMessage.error(res.message || '上传失败') }
  } catch (err) { ElMessage.error('上传失败') }
  uploading.value = false
}

async function handleUpdateMeta(row, field, value) {
  try {
    await api.put(`/doc/${row.id}/meta`, { [field]: value })
    row[field] = value
    ElMessage.success('已更新')
  } catch (e) { ElMessage.error('更新失败') }
}

async function handleDelete(row) {
  try { await deleteDoc(row.id); ElMessage.success('已删除'); load() } catch (e) { ElMessage.error('删除失败') }
}

onMounted(load)
</script>

<style scoped>
.kb-layout { display:flex; gap:16px }
.kb-sidebar { width:180px; background:#fff; border-radius:8px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,.05); flex-shrink:0; align-self:flex-start }
.cat-title { font-size:14px; font-weight:600; margin-bottom:12px; color:#303133 }
.cat-item { padding:8px 12px; border-radius:6px; cursor:pointer; font-size:13px; color:#606266; margin-bottom:4px; transition:all .15s }
.cat-item:hover { background:#ecf5ff }
.cat-item.active { background:#409eff; color:#fff }
.kb-main { flex:1; min-width:0 }
.panel { background:#fff; border-radius:8px; box-shadow:0 1px 3px rgba(0,0,0,.05); overflow:hidden }
.panel-header { padding:14px 20px; font-size:15px; font-weight:600; border-bottom:1px solid #ebeef5 }
.toolbar { display:flex; align-items:center; justify-content:space-between; padding:16px 20px; gap:12px }
.doc-link { color:#409eff; cursor:pointer; text-decoration:none }
.doc-link:hover { text-decoration:underline }
</style>
