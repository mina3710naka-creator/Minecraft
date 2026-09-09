# レイキャスト終了（実行者＝こうもり）
# プレイヤー側の処理へ実行者を戻してから発射し、こうもりは消す
execute as @a[tag=uw.instant_owner,limit=1] run function uw:crossbow/zoom/instant_launch
kill @s
