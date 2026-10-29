# lampvideomod

Minecraft(Fabric)用 Mod「Media & Display Network」。ソースは `lampvideomod/`、説明は [lampvideomod/README.md](lampvideomod/README.md)。
ビルドは `.github/workflows/build-lampvideomod.yml`(ビルド済み jar は `lampvideomod/dist/`)。

## songmod
mp3/mp4 を音符ブロック＋リピーターの演奏回路にする MOD。説明は [songmod/README.md](songmod/README.md)、ビルドは `.github/workflows/build-songmod.yml`(jar は `songmod/dist/`)。

## recmod
メニューの「録画」で、ホットバーを除いたプレイヤー視点を、音つきで録画する MOD。停止するとダウンロードフォルダーに MP4 で保存。説明は [recmod/README.md](recmod/README.md)、ビルドは `.github/workflows/build-recmod.yml`(jar は `recmod/dist/`)。

## replaymod(povreplay)
リプレイ方式の録画 MOD。プレイ中は通信と視点だけを記録して(軽い)、ワールドを出たときに再生し直して、プレイヤー視点を高画質の動画(音つき)にする。説明は [replaymod/README.md](replaymod/README.md)、ビルドは `.github/workflows/build-povreplay.yml`(jar は `replaymod/dist/`)。
