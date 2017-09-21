//: Playground - noun: a place where people can play

func diff(a: [String], b: [String]) -> ([Int], [Int], [Int]) {
    let ac: Int = a.count
    let bc: Int = b.count
    var i, j: Int
    var opt: [Int] = Array(count: (ac+1) * (bc+1), repeatedValue: 0)
    for i = ac-1 ; i >= 0 ; i-- {
        for j = bc-1 ; j >= 0 ; j-- {
            if a[i] == b[j] {
                opt[i * bc + j] = opt[(i + 1) * bc + j + 1] + 1
            } else {
                opt[i * bc + j] = max(opt[(i + 1) * bc + j], opt[i * bc + (j + 1)])
            }
        }
    }
    println(opt)
    var add: [Int] = []
    var rem: [Int] = []
    var neq: [Int] = []
    i = 0
    j = 0
    while i < ac && j < bc {
        if a[i] == b[j] {
            i++
            j++
        }
        else if opt[(i + 1) * bc + j] >= opt[i * bc + j + 1] {
            rem.append(i++)
        }
        else {
            add.append(j++)
        }
    }
    while i < ac || j < bc {
        if i == ac {
            add.append(j++)
        }
        else if j == bc {
            rem.append(i++)
        }
    }
    println(add)
    println(rem)
    for i = 0 ; i < add.count ; i++ {
        for j = i ; j < rem.count ; j++ {
            if add[i] == rem[j] {
                neq.append(add[i])
                add.removeAtIndex(i--)
                rem.removeAtIndex(j--)
                break
            }
        }
    }
    println(add)
    println(rem)
    println(neq)
    return (add, rem, neq)
}

func apply(a: [String], b: [String], diff: (add: [Int], rem: [Int], neq: [Int])) -> [String] {
    var r: [String] = a
    let ac: Int = a.count
    var add: [Int] = diff.add
    var rem: [Int] = diff.rem
    let neq: [Int] = diff.neq
    let ic: Int = add.count
    let jc: Int = rem.count
    var i: Int = ic-1
    var j: Int = jc-1
    for x in 0 ..< neq.count {
        r[neq[x]] = b[neq[x]]
    }
    while i >= 0 && j >= 0 {
        if rem[j] == add[i] {
            r[rem[j]] = b[add[i]]
            i--
            j--
        } else if rem[j] < add[i] {
            r.removeAtIndex(rem[j])
            j--
        } else if add[i] < rem[j] {
            r.insert(b[add[i]], atIndex: add[i])
            i--
        }
    }
    while i >= 0 || j >= 0 {
        if j >= 0 {
            r.removeAtIndex(rem[j])
            j--
        }
        else if i >= 0 {
            r.insert(b[add[i]], atIndex: add[i])
            i--
        }
    }
    return r
}

let a: [String] = ["abc", "b", "c", "b", "b"]
let b: [String] = ["abc", "b", "c", "a", "a", "_"]
let d = diff(a, b)
apply(a, b, d)