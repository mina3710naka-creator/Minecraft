# ============================================================
#  クリック検知（実行者＝インタラクションエンティティ）
#  attack（左クリック）/ interaction（右クリック）タグが存在するかどうかで
#  判定する。tick等のサブフィールド名に依存すると版差の影響を受けやすい
#  ため、存在チェック→即削除（消費）という安全な方式にしている
# ============================================================
execute if data entity @s attack run function uw:sword/interact/consume_attack
execute if data entity @s interaction run function uw:sword/interact/consume_interact
