module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    ecmaVersion: 'latest',
    parser: '@typescript-eslint/parser',
    sourceType: 'module'
  },
  plugins: ['@typescript-eslint', 'vue'],
  rules: {}
}
