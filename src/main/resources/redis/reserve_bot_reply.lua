local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local limit = tonumber(ARGV[1])
local skip_cooldown = ARGV[3] == '1'

if not skip_cooldown and redis.call('EXISTS', KEYS[2]) == 1 then
  return -1
end

if current >= limit then
  return -2
end

local next_count = redis.call('INCR', KEYS[1])
if next_count > limit then
  redis.call('DECR', KEYS[1])
  return -2
end

if skip_cooldown then
  return next_count
end

local cooldown_set = redis.call('SET', KEYS[2], '1', 'EX', tonumber(ARGV[2]), 'NX')
if not cooldown_set then
  redis.call('DECR', KEYS[1])
  return -1
end

return next_count
