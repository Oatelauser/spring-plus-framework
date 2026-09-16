-- pattern 必须经 ARGV 传入：Cluster 模式下 KEYS 必须是同 slot 的真实键，
-- 传 pattern 会直接抛 CROSSSLOT；SCAN 的 MATCH 参数与键路由无关，放 ARGV 是安全的
local cursor = "0"
local count = 0
repeat
    local result = redis.call("SCAN", cursor, "MATCH", ARGV[1], "COUNT", 100)
    cursor = result[1]
    local keys = result[2]
    if #keys > 0 then
        count = count + redis.call("DEL", unpack(keys))
    end
until cursor == "0"
return count
