.data
coreid: .word 0
barrier: .word 0
foo: .word 0

.text
main:
  #-------------------------------------------------------------
  # Atomic tests
  #-------------------------------------------------------------

# get a unique core id
la a0, coreid
li a1, 1
amoadd.w a2, a1, (a0)

# for now, only run this on core 0
lbl_1:
li a3, 1
bgeu a2, a3, lbl_1

lbl_2:
lw a1, (a0)
bltu a1, a3, lbl_2

test_2:
 la a0, foo
 li a5, 0xdeadbeef
 sc.w a4, a5, (a0)
 li t2, 1
 li gp, 2
 bne a4, t2, fail

test_3:
 lw a4, foo
 li t2, 0
 li gp, 3
 bne a4, t2, fail

# have each core add its coreid+1 to foo 128 times
la a0, foo
li a1, 0x80
addi a2, a2, 1
lbl_3: lr.w a4, (a0)
add a4, a4, a2
sc.w a4, a4, (a0)
bnez a4, lbl_3
addi a1, a1, -1
bnez a1, lbl_3

# wait for all cores to finish
la a0, barrier
li a1, 1
amoadd.w x0, a1, (a0)
lbl_4: lw a1, (a0)
blt a1, a3, lbl_4
fence 1, 1

test_5:
 lw a0, foo
 slli a1, a3, 6
 lbl_5: sub a0, a0, a1
 addi a3, a3, -1
 bgez a3, lbl_5
 li t2, 0
 li gp, 5
 bne a0, t2, fail

test_6:
 la a0, foo
 lbl_6: lr.w a1, (a0)
 sc.w a1, x0, (a0)
 bnez a1, lbl_6
 sc.w a1, x0 (a0)
 li t2, 1
 li gp, 6
 bne a1, t2, fail

bne zero, gp, pass
fail:
 fence 1, 1
 slli gp, gp, 0x1
 ori gp, gp, 1
 li a7, 93
 mv a0, gp
 ecall

pass:
 fence 1, 1
 li gp, 1
 li a7, 93
 li a0, 42
 ecall
