.globl main
.data
num:
    .word 0x12345678

.text
main:
    la      t0, num
retry:
    lr.w    t1, (t0)
    # increment by 1
    addi    t1, t1, 1
    sc.w    t2, t1, (t0)
    # if save fails; retry operations again
    bne     zero, t2, retry
