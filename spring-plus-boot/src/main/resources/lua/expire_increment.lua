local exists = redis.call('EXISTS', KEYS[1])
local current = redis.call('INCR', KEYS[1])
if exists == 0 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current