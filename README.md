# lampvideomod

Minecraft(Fabric)用 Mod「Media & Display Network」。ソースは `lampvideomod/`、説明は [lampvideomod/README.md](lampvideomod/README.md)。
ビルドは `.github/workflows/build-lampvideomod.yml`(ビルド済み jar は `lampvideomod/dist/`)。

## songmod
mp3/mp4 を音符ブロック＋リピーターの演奏回路にする MOD。説明は [songmod/README.md](songmod/README.md)、ビルドは `.github/workflows/build-songmod.yml`(jar は `songmod/dist/`)。

## recmod
メニューの「録画」で、ホットバーを除いたプレイヤー視点を、音つきで録画する MOD。停止するとダウンロードフォルダーに MP4 で保存。説明は [recmod/README.md](recmod/README.md)、ビルドは `.github/workflows/build-recmod.yml`(jar は `recmod/dist/`)。

## replaymod(povreplay)
リプレイ方式の録画 MOD。プレイ中は通信と視点だけを記録して(軽い)、ワールドを出たときに再生し直して、プレイヤー視点を高画質の動画(音つき)にする。説明は [replaymod/README.md](replaymod/README.md)、ビルドは `.github/workflows/build-povreplay.yml`(jar は `replaymod/dist/`)。

## magicmod(魔法MOD)
魔法の本×杖×魔法テーブル×装備×スキルツリーの魔法 MOD(8属性104魔法・物理わざ63種・アーキタイプ・使い魔・刻印・魔法合成・武器合体・属性の敵32種)。設計書と数値バランス表は `magicmod/design/`、説明は [magicmod/README.md](magicmod/README.md)、ビルドは `.github/workflows/build-magicmod.yml`(jar は `magicmod/dist/`)。

## effectmod(付与MOD)
付与台で、アイテムに「文章で書いた効果」を付ける MOD(斬撃・隕石・連鎖雷・足元にブロック設置・ポーション効果など約40種 + 剣・斧・弓・防具など17種類のアイテムごとの専用効果)。説明は [effectmod/README.md](effectmod/README.md)、ビルドは `.github/workflows/build-effectmod.yml`(jar は `effectmod/dist/`)。
