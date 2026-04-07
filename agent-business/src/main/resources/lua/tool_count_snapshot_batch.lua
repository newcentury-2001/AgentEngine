-- KEYS is empty
-- ARGV: list of count keys like tool:{toolId}:count
-- return: flat array [toolId, count, toolId, count, ...]
local result = {}
for i = 1, #ARGV do
  local ckey = ARGV[i]
  local cnt = redis.call('GETSET', ckey, '0')
  if cnt and cnt ~= '0' then
    local toolId = string.match(ckey, '^tool:(.*):count$')
    if not toolId then
      toolId = ckey
    end
    table.insert(result, toolId)
    table.insert(result, cnt)
  end
end
return result
