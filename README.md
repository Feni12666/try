# Shahadat Manage File — 10 independent Android apps

This project builds 10 separate APKs from one codebase.

Apps:
- Shahadat Manage File 01 → `com.shahadat.managefile.clone01`
- Shahadat Manage File 02 → `com.shahadat.managefile.clone02`
- Shahadat Manage File 03 → `com.shahadat.managefile.clone03`
- Shahadat Manage File 04 → `com.shahadat.managefile.clone04`
- Shahadat Manage File 05 → `com.shahadat.managefile.clone05`
- Shahadat Manage File 06 → `com.shahadat.managefile.clone06`
- Shahadat Manage File 07 → `com.shahadat.managefile.clone07`
- Shahadat Manage File 08 → `com.shahadat.managefile.clone08`
- Shahadat Manage File 09 → `com.shahadat.managefile.clone09`
- Shahadat Manage File 10 → `com.shahadat.managefile.clone10`

Each installs independently and has its own Android app-data sandbox.

## Current feature
A basic Storage Access Framework folder browser:
- Choose a folder using Android's system picker
- Remember folder permission
- List first-level files and folders
- Show file sizes

## GitHub Actions build
Push the project to a GitHub repository. The included workflow builds all 10 debug APKs and uploads one artifact named:

`Shahadat-Manage-File-10-APKs`

## Termux upload example
```bash
cd /storage/emulated/0/Download/ShahadatManageFile_10Apps
git init
git add .
git commit -m "Initial 10 app build"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

Then check the Actions tab or with GitHub CLI:

```bash
gh run list --repo YOUR_USERNAME/YOUR_REPO --limit 5
gh run watch --repo YOUR_USERNAME/YOUR_REPO
```

Important: this is a clean independent project. It is not Facebook Lite and does not copy Facebook's code, login system, or services.
