# 开发环境搭建记录

> 本文档记录本项目的开发环境基线，方便换机或他人复刻。

## 当前环境（2026-04-30 基线）

| 组件 | 版本 | 安装方式 |
|------|------|---------|
| macOS | (本机版本) | — |
| Homebrew | 4.x | 官方脚本 |
| Git | 2.54.0 | `brew install git` |
| OpenJDK | 21.0.11 (Homebrew) | `brew install openjdk@21` |
| GitHub 认证 | SSH (ed25519) | 本地密钥 + GitHub Settings |

## 一次性环境搭建步骤

### 1. 安装 Homebrew

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

安装完成后按提示配置 PATH（写入 `~/.zprofile`）。

### 2. 安装 Git

```bash
brew install git
```

### 3. 配置 Git 全局身份

```bash
git config --global user.name "Felix"
git config --global user.email "subaru3zz@outlook.com"
```

### 4. 配置 GitHub SSH 认证

```bash
ssh-keygen -t ed25519 -C "subaru3zz@outlook.com"
eval "$(ssh-agent -s)"
ssh-add --apple-use-keychain ~/.ssh/id_ed25519
pbcopy < ~/.ssh/id_ed25519.pub
```

把剪贴板里的公钥粘贴到 GitHub → Settings → SSH and GPG keys → New SSH key。

验证连接：

```bash
ssh -T git@github.com
```

### 5. 安装 OpenJDK 21

```bash
brew install openjdk@21
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

### 6. 配置 `~/.zshrc`

```bash
# ===== Homebrew =====
eval "$(/opt/homebrew/bin/brew shellenv)"

# ===== Java (JDK 21) =====
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"
```

执行 `source ~/.zshrc` 让配置生效。

## 关键技术选型

- **JDK 21 而非 17 或 24**：21 是 LTS 长期支持版（维护至 2031），并支持虚拟线程，与 ADR-001 中流式输出方案的升级路径对齐。
- **SSH 而非 HTTPS**：免去每次推送输入 Token 的麻烦，长期开发首选。
- **Homebrew 而非 Xcode CLT**：版本更新及时，未来安装 Maven / Redis / Ollama 等工具栈也将统一通过 Homebrew 管理。

## 验证清单

```bash
brew --version    # Homebrew 4.x
git --version     # 2.5x.x
java --version    # openjdk 21.0.11
echo $JAVA_HOME   # /opt/homebrew/opt/openjdk@21
ssh -T git@github.com  # Hi felixQAQ-edu! ...
```

---

_最后更新：2026-04-30 · 完成 M1 启动前的环境基线搭建_