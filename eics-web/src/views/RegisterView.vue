<template>
  <div class="reg-container">
    <el-card class="reg-card">
      <template #header><h2>EICS 用户注册</h2></template>
      <el-form ref="f" :model="form" :rules="rules" label-width="0" @keyup.enter="handleReg">
        <el-form-item prop="username"><el-input v-model="form.username" placeholder="用户名" /></el-form-item>
        <el-form-item prop="password"><el-input v-model="form.password" type="password" placeholder="密码（至少6位）" show-password /></el-form-item>
        <el-form-item prop="name"><el-input v-model="form.name" placeholder="姓名" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" style="width:100%" @click="handleReg">注 册</el-button></el-form-item>
      </el-form>
      <div v-if="err" class="err">{{ err }}</div>
      <div class="link">已有账号？<router-link to="/login">去登录</router-link></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const err = ref('')
const form = reactive({ username: '', password: '', name: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

async function handleReg() {
  err.value = ''
  loading.value = true
  try {
    const res = await api.post('/auth/register', { ...form })
    if (res.data.code === 200) {
      const d = res.data.data
      auth.login(d.token, d.user_id, d.name, d.role)
      router.push('/')
    } else { err.value = res.data.message || '注册失败' }
  } catch (e) { err.value = e.response?.data?.message || '网络错误' }
  loading.value = false
}
</script>

<style scoped>
.reg-container { display:flex; justify-content:center; align-items:center; height:100vh; background:#f0f2f5 }
.reg-card { width:400px }
.reg-card h2 { margin:0; text-align:center; font-size:20px; color:#303133 }
.err { color:#f56c6c; font-size:13px; text-align:center; margin-top:-8px }
.link { text-align:center; margin-top:12px; font-size:13px; color:#909399 }
.link a { color:#409eff }
</style>
