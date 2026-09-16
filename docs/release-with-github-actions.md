# 通过 GitHub Actions 打 tag 自动发布 Maven Central 完整指南

> 本文是**可复用的发布 SOP**：任何新的 Maven 公共库项目都可以照本宣科接入同一套「打 tag → CI 自动构建/签名/上传 → Portal 一键 Publish」流水线。文中所有坑均为 spring-plus-framework 1.0.0 发布过程中实际踩过并验证的。

---

## 目录

1. [整体架构](#1-整体架构)
2. [一次性准备：Central 账号、Token、GPG 密钥](#2-一次性准备central-账号tokengpg-密钥)
3. [工程配置：pom 侧的发布基建](#3-工程配置pom-侧的发布基建)
4. [仓库配置：GitHub Secrets（占位符）](#4-仓库配置github-secrets占位符)
5. [CI 脚本：release.yml 全文与逐段解释](#5-ci-脚本releaseyml-全文与逐段解释)
6. [日常发布 SOP：三条命令](#6-日常发布-sop三条命令)
7. [失败处理与重试](#7-失败处理与重试)
8. [安全注意事项](#8-安全注意事项)
9. [新项目接入 Checklist](#9-新项目接入-checklist)

---

## 1. 整体架构

```
开发者本机                          GitHub                          Sonatype Central
──────────────                    ────────                        ────────────────
git tag v1.0.0
git push origin v1.0.0  ──────►  tag 推送触发 release.yml
                                  │
                                  ├─ 下载 Secrets（占位符注入，全程不落明文）
                                  │    ├ CENTRAL_USERNAME ┐
                                  │    ├ CENTRAL_PASSWORD ┴→ 渲染 runner 临时 settings.xml
                                  │    └ GPG_PRIVATE_KEY ──→ gpg --import（签名用）
                                  │
                                  ├─ mvn -P release deploy
                                  │    ├ maven-source-plugin   附源码 jar
                                  │    ├ maven-javadoc-plugin  附 javadoc jar
                                  │    ├ maven-gpg-plugin      全构件签名（.asc）
                                  │    └ central-publishing    打包上传 bundle
                                  ▼
                                  deployment 状态：UPLOADING → VALIDATED
                                                                  │
                                  开发者在 Portal 点 Publish ←─────┘
                                                                  ▼
                                                          10~30 分钟后全球可检索
```

**职责边界**：CI 负责「构建 + 签名 + 上传 + 校验」，**Publish 由人最终确认**（Central 发布不可撤销，保留人工闸门；完全无人值守见 §7 的 autoPublish 选项）。

---

## 2. 一次性准备：Central 账号、Token、GPG 密钥

### 2.1 Central 账号与命名空间

1. 打开 [central.sonatype.com](https://central.sonatype.com)，**用 GitHub 账号登录**（注册即完成）
2. 进入 **Namespaces** 页：GitHub 登录方式会自动创建并验证 `io.github.<你的GitHub用户名>` 命名空间（无需手工验证；若显示 Pending，按页面提示建一个临时验证仓库即可）
3. 命名空间即你发布构件的 groupId 上限——`io.github.oatelauser` 下的任意 groupId 都可发布

### 2.2 生成 User Token（发布凭据）

Central 网站 → 右上角头像 → **Account** → **Generate User Token**，得到一对凭据：

```
username: xxxxxxxx            → 对应 Secret：CENTRAL_USERNAME
password: yyyyyyyyyyyyyyyy    → 对应 Secret：CENTRAL_PASSWORD（只显示一次，立即保存）
```

这对 token 同时用于**本地手动 deploy**（写进 `~/.m2/settings.xml`）和 **CI deploy**（写进 GitHub Secrets）：

```xml
<!-- C:\Users\<你>\.m2\settings.xml —— 本地手动 deploy 用；CI 上由 workflow 动态渲染，不需要此文件 -->
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <servers>
    <server>
      <id>central</id>
      <username>Token 的 username</username>
      <password>Token 的 password</password>
    </server>
  </servers>
</settings>
```

> `<id>central</id>` 必须与 pom 中 central-publishing 插件的 `publishingServerId` 一致。

### 2.3 GPG 签名密钥

**必须在 Maven 使用的那个 shell 环境里生成**（Windows 上即 Git Bash——如果你在别的 MSYS/Cygwin 环境生成，Maven 会看不见它，这是实际踩过的坑）。Central 要求密钥 ≥2048 位、公钥发布到公开 keyserver。

在 **Git Bash** 中执行：

```bash
# 生成（免口令版：CI 里无需再配置 passphrase，适合个人发布机）
gpg --batch --gen-key <<'EOF'
Key-Type: RSA
Key-Length: 3072
Name-Real: 你的名字
Name-Email: 你的邮箱
Expire-Date: 2y
%no-protection
Key-Usage: sign
EOF

# 记下输出的密钥 ID（如 1CC2D0AD70C3FEA3）
gpg --list-secret-keys --keyid-format long

# 公钥发布到 keyserver（Central 校验签名的来源）
gpg --keyserver keyserver.ubuntu.com --send-keys <密钥ID>

# 回查确认（应显示已存在）
gpg --keyserver keyserver.ubuntu.com --recv-keys <密钥ID>
```

> 带口令密钥也可以，但 CI 需要额外配 `GPG_PASSPHRASE` Secret 并在 gpg 导入与 maven-gpg-plugin 两侧使用 `--pinentry-mode loopback`，本文的免口令方案省去这一层。

---

## 3. 工程配置：pom 侧的发布基建

### 3.1 根 pom 必备元数据

Central 校验要求以下字段齐全（缺失会被拒）：

```xml
<groupId>io.github.<user></groupId>
<artifactId>xxx</artifactId>
<packaging>pom</packaging>
<name>...</name>
<description>...</description>
<url>https://github.com/<user>/<repo></url>

<licenses>
    <license>
        <name>Apache License, Version 2.0</name>
        <url>https://www.apache.org/licenses/LICENSE-2.0</url>
    </license>
</licenses>
<developers>
    <developer>
        <id><user></id>
        <name><user></name>
        <url>https://github.com/<user></url>
    </developer>
</developers>
<scm>
    <url>https://github.com/<user>/<repo></url>
    <connection>scm:git:https://github.com/<user>/<repo>.git</connection>
    <developerConnection>scm:git:git@github.com:<user>/<repo>.git</developerConnection>
</scm>
```

### 3.2 release profile（四插件组合）

```xml
<profiles>
    <profile>
        <id>release</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.sonatype.central</groupId>
                    <artifactId>central-publishing-maven-plugin</artifactId>
                    <version>0.8.0</version>
                    <extensions>true</extensions>
                    <configuration>
                        <publishingServerId>central</publishingServerId>
                        <!-- 不发布的模块（见 §3.3 坑 3） -->
                        <excludeArtifacts>
                            <artifactId>你的示例模块artifactId</artifactId>
                        </excludeArtifacts>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-source-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>attach-sources</id>
                            <goals><goal>jar-no-fork</goal></goals>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-javadoc-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>attach-javadocs</id>
                            <goals><goal>jar</goal></goals>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-gpg-plugin</artifactId>
                    <version>3.2.7</version>
                    <executions>
                        <execution>
                            <id>sign-artifacts</id>
                            <goals><goal>sign</goal></goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
        <distributionManagement>
            <snapshotRepository>
                <id>central</id>
                <url>https://central.sonatype.com/repository/maven-snapshots/</url>
            </snapshotRepository>
        </distributionManagement>
    </profile>
</profiles>
```

### 3.3 三个真实踩过的坑（重要）

| # | 坑 | 现象 | 正解 |
|---|---|---|---|
| 1 | **`maven.deploy.skip=true` 对 central-publishing 无效** | 示例模块混入 bundle，又没有 sources/javadoc，**整个 deployment 被 Central 校验拒绝** | 用插件的 `excludeArtifacts`（根 pom 配置） |
| 2 | **`skipPublishing=true` 会跳过整个上传** | 设在示例模块上，而 bundle 的打包上传恰好发生在 **reactor 最后一个模块**的执行里 → 构建显示 SUCCESS 但**什么都没上传**（假成功） | 不要用它来排除模块；它只用于整体跳过发布 |
| 3 | **`excludeArtifacts` 按 artifactId 匹配**（字节码实证：`excludeArtifacts.contains(artifact.getArtifactId())`） | 写 GAV 全坐标不生效 | 子元素 `<artifactId>xxx</artifactId>` 只写 artifactId |

另一个前置校验要求：**bundle 内每个组件都必须带 sources 和 javadoc**——所以排除任何"非正式构件"模块是硬需求。

---

## 4. 仓库配置：GitHub Secrets（占位符）

进入仓库 **Settings → Secrets and variables → Actions → New repository secret**，配置三个：

| Secret 名 | 值的来源 |
|---|---|
| `CENTRAL_USERNAME` | §2.2 的 Token username（即 `~/.m2/settings.xml` 里 `<username>`） |
| `CENTRAL_PASSWORD` | §2.2 的 Token password |
| `GPG_PRIVATE_KEY` | Git Bash 中 `gpg --armor --export-secret-keys <密钥ID>` 的完整输出（`-----BEGIN PGP PRIVATE KEY BLOCK-----` 到 `-----END-----` 整块） |

导出私钥的规范操作（避免误提交进仓库）：

```bash
# Git Bash：导出到仓库外的用户目录
gpg --armor --export-secret-keys <密钥ID> > "$USERPROFILE/gpg-private-key.asc"
# 记事本打开、整块复制到 GitHub Secret 后，立即删除
notepad "$USERPROFILE/gpg-private-key.asc"   # 或 Windows 下 del %USERPROFILE%\gpg-private-key.asc
rm "$USERPROFILE/gpg-private-key.asc"
```

**为什么安全**：Secrets 值只存在于 GitHub 加密存储，workflow 里经 `${{ secrets.XXX }}` 注入为 runner 环境变量；日志中自动打码为 `***`；来自 fork 的 PR 无法读取；仓库代码里零明文。

---

## 5. CI 脚本：release.yml 全文与逐段解释

文件位置：`.github/workflows/release.yml`（完整可运行版即本仓库该文件）

```yaml
name: Release to Maven Central

on:
  push:
    tags: ['v*']        # ← 只有打 tag 才触发；普通 push / PR 永不触发（防误发布）

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4        # 官方 action：装 JDK + 缓存 ~/.m2 依赖
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      # 步骤①：把签名私钥注入 runner 的 gpg 钥匙环（§2.3 免口令密钥，导入即用）
      - name: Import GPG signing key
        env:
          GPG_PRIVATE_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
        run: |
          echo "$GPG_PRIVATE_KEY" | gpg --batch --import
          gpg --list-secret-keys --keyid-format long

      # 步骤②：用 Secrets 动态渲染 settings.xml（runner 上不存在本机那份）
      - name: Generate settings.xml from secrets
        env:
          CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          CENTRAL_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/settings.xml <<EOF
          <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
            <servers>
              <server>
                <id>central</id>
                <username>${CENTRAL_USERNAME}</username>
                <password>${CENTRAL_PASSWORD}</password>
              </server>
            </servers>
          </settings>
          EOF

      # 步骤③：构建 + 测试 + 附源码/文档 + 签名 + 上传 Central（一条命令全链路）
      - name: Build, sign and deploy
        run: mvn -B -P release deploy

      - name: Upload deploy log on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: release-deploy-log
          path: |
            **/target/central-publishing/**
          retention-days: 7
```

**逐段要点**：

- **`tags: ['v*']`**：触发器收敛到"维护者推送 tag"这一个动作——这是整套方案的安全锚点
- **步骤①②** 就是"占位符替换"的实现：Secrets → env → gpg 钥匙环 / settings.xml 文件，runner 用完即焚
- **步骤③** `-P release deploy` 会跑完整生命周期：**含全部测试**，测试红则不会发布（质量闸门内置于流程）
- 上传成功后插件会轮询到 **VALIDATED** 即退出（`waitForPublishCompletion` 默认 false），workflow 显示绿 = deployment 已就绪待 Publish

---

## 6. 日常发布 SOP：三条命令

CI 配好后，每次发版只需要：

```bash
# ① 摘掉版本快照后缀（SNAPSHOT → 正式版），并让 README 等文档里的版本号同步
mvn versions:set -DremoveSnapshot -DgenerateBackupPoms=false
# （或指定下一版：mvn versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false）

# ② 提交
git commit -am "release: 1.0.0"

# ③ 打 tag 并推送 —— 推送瞬间 CI 自动接管
git tag v1.0.0 && git push origin main v1.0.0
```

之后：

1. 去 **Actions** 页看 `Release to Maven Central` 变绿（构建+测试+签名+上传约 2~4 分钟）
2. 去 [Central → Publishing → Deployments](https://central.sonatype.com/publishing/deployments)，对状态 **VALIDATED** 的 deployment 点 **Publish**（不可撤销，发布前最后确认点）
3. 10~30 分钟后 [central.sonatype.com/search?q=io.github.xxx](https://central.sonatype.com/search) 可检索；`search.maven.org` 同步稍慢
4. 回到 main 推进下一开发周期：`mvn versions:set -DnewVersion=1.0.1-SNAPSHOT && git commit -am "chore: next dev cycle" && git push`

**版本号规范**：tag 名 `v1.0.0` 必须与 pom 里 `<version>1.0.0</version>` 一致——CI 用的是 **tag 指向的那个提交里的 pom 版本**，先改版本、提交、再打 tag，顺序不能乱。

---

## 7. 失败处理与重试

| 场景 | 处理 |
|---|---|
| workflow 红（测试/构建/签名失败） | Actions → 点进失败 run 看日志；修复后对该 run 点 **Re-run jobs**（同一 tag 引用，无需重新打 tag） |
| Secrets 配置晚于 tag 推送（首跑必红） | 同上，配好 Secrets 后 Re-run 即可 |
| 上传被 Central 校验拒绝（FAILED） | 日志会给出组件级原因（缺 sources/javadoc、签名无效、坐标非法）；修 pom 后**删 tag 重推**：`git tag -f v1.0.0 && git push origin v1.0.0 --force`（本地同步 `git tag -d` 后重打） |
| Portal 上有废弃/失败的 deployment | 点 **Drop** 清理；**已 Publish 的不可撤**（只能发布新版本覆盖迭代） |
| 想完全无人值守（连 Publish 也自动） | central-publishing 插件加 `<autoPublish>true</autoPublish>`——建议先人工把关若干版本再开启 |

---

## 8. 安全注意事项

1. **Secrets 最小暴露面**：只有 `v*` tag 触发的 workflow 引用它们；不要把 release workflow 的触发条件放宽到 push/PR
2. **私钥导出文件即用即删**：导出到仓库目录外（如 `%USERPROFILE%`），粘贴进 Secret 后立即删除，防止被 `git add -A` 误提交
3. **Token 疑似泄露立即轮换**：Central → Account → Generate User Token 生成新对（旧的自动作废），同步更新 GitHub Secrets **和** 本机 `~/.m2/settings.xml`
4. **GPG 密钥备份**：`gpg --armor --export-secret-keys > backup.asc` 离线保存一份（U 盘/密码管理器附件）；密钥丢失则无法再以同一身份签名发版
5. **本地 settings.xml 不要提交进任何仓库**（`.gitignore` 加 `.m2/` 或干脆不放项目目录）

---

## 9. 新项目接入 Checklist

照抄本仓库（[spring-plus-framework](https://github.com/Oatelauser/spring-plus-framework)）即可：

- [ ] **Central**：GitHub 账号登录 [central.sonatype.com](https://central.sonatype.com)，确认 `io.github.<user>` 命名空间 Verified；Generate User Token 存好
- [ ] **GPG**：Git Bash 里生成免口令密钥（§2.3 命令块），公钥 send-keys 到 keyserver.ubuntu.com
- [ ] **本机 settings.xml**：写入 central server（§2.2 XML 片段）
- [ ] **pom**：元数据五件套（url/license/developers/scm/description，§3.1）+ release profile（§3.2，四插件照抄）+ 示例/非发布模块进 `excludeArtifacts`（§3.3）
- [ ] **验证构建**：本地跑通 `mvn -P release deploy`（至少 BUILD SUCCESS、日志出现 deploymentId 与 VALIDATED）
- [ ] **CI**：复制本仓库 `.github/workflows/ci.yml`（日常构建）与 `release.yml`（tag 发布）到新仓库 `.github/workflows/`
- [ ] **Secrets**：新仓库配 `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` / `GPG_PRIVATE_KEY` 三个（§4）
- [ ] **首次发布**：按 §6 三条命令走一遍，Portal 点 Publish 前确认 Actions 绿灯
