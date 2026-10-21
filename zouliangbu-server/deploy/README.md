# 部署手册

一条命令把当前代码发到 `82.156.84.127` 并重启服务。

```bash
cd zouliangbu-server
./deploy/release.sh              # Windows: .\deploy\release.ps1
```

脚本做五件事：`mvn clean package` → scp 上传 → 原子切软链 → `systemctl restart` → 健康检查。
**起不来自动回滚**到上一版本并打印 60 行日志。

## 首次接入（只做一次）

用 **ubuntu** 用户（非 root），因此需要 sudo 权限。

### 1. 授权公钥

本机公钥（`~/.ssh/id_ed25519.pub`）：

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIQ7kGxFTVgyyun2x/IUuODs2MgutJ3KAVHMhFSLsuwW 15797@zyl
```

在服务器上（用 ubuntu 登录）：

```bash
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIQ7kGxFTVgyyun2x/IUuODs2MgutJ3KAVHMhFSLsuwW 15797@zyl' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

### 2. 配 sudo 免密

部署脚本要写 `/opt`、重启 systemd，全程不能卡在输密码上：

```bash
echo 'ubuntu ALL=(ALL) NOPASSWD:ALL' | sudo tee /etc/sudoers.d/zlb-deploy
sudo chmod 440 /etc/sudoers.d/zlb-deploy
sudo -n true && echo OK
```

本机验证登录：

```bash
ssh ubuntu@82.156.84.127 "echo OK; sudo -n whoami"
```

### 3. 本机部署配置

```bash
cd zouliangbu-server/deploy
cp deploy.env.example deploy.env    # 默认已是 ubuntu / 82.156.84.127 / 22
```

`deploy.env` 已 gitignore，只放「连哪台机器」，不含密码。

### 4. 服务器初始化

```bash
./deploy/bootstrap.sh --from-local
```

做四件事：装 JDK 17（已有则跳过）→ 建 `zlb` 用户和 `releases/ uploads/` 目录 →
把旧的直放 jar 迁进 `releases/legacy-*`（保留为回滚基线）→ 装 systemd 单元。

然后**必须**改配置，否则 prod 连不上数据库：

```bash
ssh ubuntu@82.156.84.127 "sudo -e /opt/zouliangbu-server/zlb.env"
#   DATABASE_PASSWORD=真实密码
#   ADMIN_PASSWORD=真实后台密码（别留 admin）
ssh ubuntu@82.156.84.127 "sudo chmod 600 /opt/zouliangbu-server/zlb.env"
```

### 5. 首次发布

```bash
./deploy/release.sh
```

## 常用

```bash
./deploy/release.sh --skip-tests    # 跳过单测，赶时间用
./deploy/release.sh --rollback      # 回滚到上一版本
ssh ubuntu@82.156.84.127 "sudo journalctl -u zouliangbu-server -f"   # 实时日志
ssh ubuntu@82.156.84.127 "systemctl status zouliangbu-server"        # 运行状态
```

## 服务器结构

```
/opt/zouliangbu-server/
├── releases/
│   ├── 20261021-153000-a1b2c3/app.jar    # 每次发布一个目录，保留最近 5 个
│   └── legacy-20261021-140000/app.jar    # 首次 bootstrap 时迁移的旧版
├── current -> releases/20261021-153000-a1b2c3
├── zlb.env          # 数据库密码 / 后台密码，chmod 600
└── uploads/         # 后台图片，跨版本共享，发布不动
```

`ExecStart` 指向 `current/app.jar`，切换软链即切版本。

## 排查

| 现象 | 原因 |
|---|---|
| `Permission denied (publickey,password)` | 第 1 步公钥没加到 ubuntu 用户的 `authorized_keys` |
| `ubuntu 无法免密 sudo` | 第 2 步 sudoers 没配 |
| `远端缺 zlb.env` | 先跑 `bootstrap.sh`，再填密码 |
| 健康检查超时但日志正常 | 端口被占；`ss -lntp \| grep 8080` 看是不是有个旧进程 |
| `Permission denied` 读 `current/app.jar` | 新 jar 属主不对。脚本已 `chown zlb:zlb`，手工补救：`sudo chown -R zlb:zlb /opt/zouliangbu-server/releases` |
| Flyway 报 migration 校验失败 | 改过已发布的 `V*.sql`。禁止改历史脚本，只能加新的 |
| 发布后还跑旧版本 | 检查 `ls -l /opt/zouliangbu-server/current` 指向，以及 unit 里 `-jar` 是否指向 `current/app.jar` |

### 软链切换为什么不用 `ln -sfn` 直接覆盖

`ln -sfn A current` 在 `current` **不存在**时，`-n` 不生效：
它会把软链建到 `A/` 目录**内部**（即 `A/current`），而不是在当前目录建 `current`。
所以脚本一律走「先 `ln -s` 到 `.current.next`，再 `mv -Tf` 覆盖」，两步都是确定行为。

## 注意事项

- **脚本不碰 git**。提交和推 GitHub 仍需明确指令后再做，两者相互独立。
- **不要改已发布的 Flyway 脚本**。`V1~V4` 已在线上跑过，改动会导致 checksum 不匹配直接拒启。要改表就加 `V5__xxx.sql`。
- 线上目前是 **prod + MySQL**。若那台机器没装 MySQL，把 `zlb.env` 里 profile 改成 `dev` 并设 `SQLITE_PATH`，SQLite 单文件同样能跑。
