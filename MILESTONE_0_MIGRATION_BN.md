# Milestone 0 — বর্তমান ডাটা নিরাপদ রাখার নিয়ম

বর্তমান working V3 থেকে নেওয়া `app-data.tar` এবং APK backup অপরিবর্তিত রাখবেন।

SHAHADAT PRO একই package id (`com.nagram.usbbridge`), একই `bridge` preferences এবং একই `bridge_journal.db` ব্যবহার করে। তাই permanent-signing migration-এর পরে backup restore করলে পুরোনো transfer journal/settings নতুন Compose shell দেখতে পারবে।

গুরুত্বপূর্ণ:

1. পুরোনো temporary-signed app থাকা অবস্থায় permanent-signed APK direct update নাও নিতে পারে।
2. uninstall করার আগে fresh backup নিতে হবে।
3. permanent-signed SHAHADAT PRO install করার পরে প্রয়োজনে Termux/Shizuku `run-as` দিয়ে `app-data.tar` restore করা হবে।
4. uninstall করলে Android-এর Shizuku/SAF grants revoke হতে পারে; restore-এর পরে Shizuku permission এবং USB folder আবার grant করতে হতে পারে।
5. Pendrive-এর existing file কখনো migration process-এর অংশ হিসেবে delete করা হবে না।
