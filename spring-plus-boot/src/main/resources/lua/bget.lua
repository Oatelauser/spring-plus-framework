local cursor = "0"
local result = {}
local index = 1

repeat
    local scan_result = redis.call("SCAN", cursor, "MATCH", ARGV[1], "COUNT", 100)
    cursor = scan_result[1]
    local keys = scan_result[2]

    for i, key in ipairs(keys) do
        local value = redis.call("GET", key)
        result[index] = {key, value}
        index = index + 1
    end
until cursor == "0"

return result