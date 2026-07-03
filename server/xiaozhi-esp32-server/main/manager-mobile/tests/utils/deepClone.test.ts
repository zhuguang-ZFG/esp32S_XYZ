import { describe, expect, it } from 'vitest'
import { deepClone } from '@/utils'

describe('deepClone', () => {
  it('clones primitives', () => {
    expect(deepClone(1)).toBe(1)
    expect(deepClone('a')).toBe('a')
    expect(deepClone(null)).toBe(null)
    expect(deepClone(undefined)).toBe(undefined)
  })

  it('clones plain objects without shared references', () => {
    const obj = { a: 1, b: { c: 2 } }
    const cloned = deepClone(obj)
    expect(cloned).toEqual(obj)
    expect(cloned).not.toBe(obj)
    expect(cloned.b).not.toBe(obj.b)
    cloned.b.c = 3
    expect(obj.b.c).toBe(2)
  })

  it('clones arrays without shared references', () => {
    const arr = [1, { a: 2 }]
    const cloned = deepClone(arr)
    expect(cloned).toEqual(arr)
    expect(cloned).not.toBe(arr)
    cloned[1].a = 3
    expect(arr[1].a).toBe(2)
  })

  it('clones Date instances', () => {
    const date = new Date('2026-07-03')
    const cloned = deepClone(date)
    expect(cloned).toEqual(date)
    expect(cloned).not.toBe(date)
  })
})
