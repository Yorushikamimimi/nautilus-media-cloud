# 推送到你的 GitHub

本地已完成首次提交。按下面步骤把仓库推到你的 GitHub：

1. **在 GitHub 上新建仓库**
   - 打开 https://github.com/new
   - 仓库名建议：`nautilus-media-cloud`
   - 不要勾选 “Add a README” / “Initialize with .gitignore”（本地已有）
   - 创建后记下仓库地址，例如：`https://github.com/你的用户名/nautilus-media-cloud.git`

2. **添加远程并推送**
   在项目根目录执行（把 `你的用户名` 换成你的 GitHub 用户名）：

   ```bash
   git remote add origin https://github.com/你的用户名/nautilus-media-cloud.git
   git branch -M main
   git push -u origin main
   ```

   若使用 SSH：

   ```bash
   git remote add origin git@github.com:你的用户名/nautilus-media-cloud.git
   git branch -M main
   git push -u origin main
   ```

3. **若推送时要求登录**
   - HTTPS：按提示输入 GitHub 用户名与 **Personal Access Token**（不再使用账号密码）
   - SSH：需先在 GitHub 添加本机公钥

推送完成后，在 GitHub 仓库页即可看到完整项目与 README。
