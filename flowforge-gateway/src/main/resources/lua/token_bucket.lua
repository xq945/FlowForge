-- 令牌桶限流
--
-- 时间戳取 Redis 服务器时间而不是调用方本地时间：网关多实例部署时，
-- 各实例的时钟漂移会让同一个桶被"补"出多余令牌，限流就形同虚设。
--
-- KEYS[1] 限流键
-- ARGV[1] 每秒补充令牌数（replenishRate）
-- ARGV[2] 桶容量（burstCapacity）
-- ARGV[3] 本次请求消耗的令牌数（requestedTokens）
-- ARGV[4] 键过期秒数（ttl）
--
-- 返回 { 是否放行(1/0), 剩余令牌整数, 建议等待毫秒数 }

local key = KEYS[1]
local replenish = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(state[1])
local last = tonumber(state[2])

if tokens == nil then
  -- 键不存在（首次请求或已过期）：以满桶开始
  tokens = capacity
  last = now
end

local delta = math.max(0, now - last)
if delta > 0 then
  tokens = math.min(capacity, tokens + delta * replenish / 1000)
end

local allowed = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
end

redis.call('HMSET', key, 'tokens', string.format('%.3f', tokens), 'ts', now)
redis.call('EXPIRE', key, ttl)

local retryAfter = 0
if allowed == 0 then
  -- 攒够本次所需令牌还要多久
  retryAfter = math.ceil((requested - tokens) * 1000 / replenish)
end

return { allowed, math.floor(tokens), retryAfter }
