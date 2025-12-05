/*
 * Snes9x - Portable Super Nintendo Entertainment System (TM) emulator.
 *
 * (c) Copyright 1996 - 2001 Gary Henderson (gary.henderson@ntlworld.com) and
 *                           Jerremy Koot (jkoot@snes9x.com)
 *
 * Super FX C emulator code 
 * (c) Copyright 1997 - 1999 Ivar (ivar@snes9x.com) and
 *                           Gary Henderson.
 * Super FX assembler emulator code (c) Copyright 1998 zsKnight and _Demo_.
 *
 * DSP1 emulator code (c) Copyright 1998 Ivar, _Demo_ and Gary Henderson.
 * C4 asm and some C emulation code (c) Copyright 2000 zsKnight and _Demo_.
 * C4 C code (c) Copyright 2001 Gary Henderson (gary.henderson@ntlworld.com).
 *
 * DOS port code contains the works of other authors. See headers in
 * individual files.
 *
 * Snes9x homepage: http://www.snes9x.com
 *
 * Permission to use, copy, modify and distribute Snes9x in both binary and
 * source form, for non-commercial purposes, is hereby granted without fee,
 * providing that this license information and copyright notice appear with
 * all copies and any derived work.
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event shall the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Snes9x is freeware for PERSONAL USE only. Commercial users should
 * seek permission of the copyright holders first. Commercial use includes
 * charging money for Snes9x or software derived from Snes9x.
 *
 * The copyright holders request that bug fixes and improvements to the code
 * should be forwarded to them so everyone can benefit from the modifications
 * in future versions.
 *
 * Super NES and Super Nintendo Entertainment System are trademarks of
 * Nintendo Co., Limited and its subsidiary companies.
 */

#include "snes9x.h"
#include "memmap.h"
#include "ppu.h"
#include "cpuexec.h"

#include "sa1.h"

static void S9xSA1CharConv2 ();
static void S9xSA1DMA ();
static void S9xSA1ReadVariableLengthData (bool8 inc, bool8 no_shift);

void S9xSA1Init ()
{
    SA1.NMIActive = FALSE;
    SA1.IRQActive = FALSE;
    SA1.WaitingForInterrupt = FALSE;
    SA1.Waiting = FALSE;
    SA1.Flags = 0;
    SA1.Executing = FALSE;
    memset (&Memory.FillRAM [0x2200], 0, 0x200);
    Memory.FillRAM [0x2200] = 0x20;
    Memory.FillRAM [0x2220] = 0x00;
    Memory.FillRAM [0x2221] = 0x01;
    Memory.FillRAM [0x2222] = 0x02;
    Memory.FillRAM [0x2223] = 0x03;
    Memory.FillRAM [0x2228] = 0xff;
    SA1.op1 = 0;
    SA1.op2 = 0;
    SA1.arithmetic_op = 0;
    SA1.sum = 0;
    SA1.overflow = FALSE;
}

void S9xSA1Reset ()
{
    SA1Registers.PB = 0;
    SA1Registers.PC = Memory.FillRAM [0x2203] |
		      (Memory.FillRAM [0x2204] << 8);
    SA1Registers.D.W = 0;
    SA1Registers.DB = 0;
    SA1Registers.SH = 1;
    SA1Registers.SL = 0xFF;
    SA1Registers.XH = 0;
    SA1Registers.YH = 0;
    SA1Registers.P.W = 0;

    SA1.ShiftedPB = 0;
    SA1.ShiftedDB = 0;
    SA1SetFlags (Memory.FillRAM [0x2205] | 0x30);
    SA1.Executing = TRUE;
    SA1.NMIActive = FALSE;
    SA1.IRQActive = FALSE;
    SA1.WaitingForInterrupt = FALSE;
    SA1.Waiting = FALSE;
    SA1.op1 = 0;
    SA1.op2 = 0;
    SA1.arithmetic_op = 0;
    SA1.sum = 0;
    SA1.overflow = FALSE;

    S9xSA1SetPCBase (SA1Registers.PC);
}

void S9xSA1SetBWRAMMemMap (uint8 val)
{
    int c;

    if (val & 0x80)
    {
	for (c = 0; c < 0x400; c += 16)
	{
	    SA1.Map [c + 6] = SA1.Map [c + 0x806] = &Memory.BWRAM[(val & 0x7f) * 0x2000 / 4];
	    SA1.WriteMap [c + 6] = SA1.WriteMap [c + 0x806] = &Memory.BWRAM[(val & 0x7f) * 0x2000 / 4];
	}
	SA1.BWRAM = &Memory.BWRAM[(val & 0x7f) * 0x2000 / 4];
    }
    else
    {
	for (c = 0; c < 0x400; c += 16)
	{
	    SA1.Map [c + 6] = SA1.Map [c + 0x806] = &Memory.BWRAM[0];
	    SA1.WriteMap [c + 6] = SA1.WriteMap [c + 0x806] = &Memory.BWRAM[0];
	}
	SA1.BWRAM = &Memory.BWRAM[0];
    }
}

void S9xFixSA1AfterSnapshotLoad ()
{
    SA1.ShiftedPB = (uint32) SA1Registers.PB << 16;
    SA1.ShiftedDB = (uint32) SA1Registers.DB << 16;

    // CORREÇÃO: Usar SA1Registers.PC (uint32) em vez de SA1.PC (ponteiro)
    S9xSA1SetPCBase (SA1Registers.PC); 
    S9xSA1SetBWRAMMemMap (Memory.FillRAM [0x2225]);

    if (Memory.FillRAM [0x2225] & 0x80)
	SA1.BWRAM = &Memory.BWRAM[(Memory.FillRAM [0x2225] & 0x7f) * 0x2000 / 4];
    else
	SA1.BWRAM = &Memory.BWRAM[0];
}

uint8 S9xSA1GetByte (uint32 address)
{
    uint8 *GetAddress = SA1.Map [(address >> 12) & 0xfff];
    if (GetAddress >= (uint8 *) CMemory::MAP_LAST)
	return (*(GetAddress + (address & 0xffff)));

    // CORREÇÃO: Cast para long para compatibilidade com 64-bits
    switch ((long) GetAddress)
    {
    case CMemory::MAP_PPU:
	return (S9xGetSA1 (address & 0xffff));
    case CMemory::MAP_LOROM_SRAM:
    case CMemory::MAP_SA1RAM:
	return (*(Memory.SRAM + (address & 0xffff)));
    case CMemory::MAP_BWRAM:
	return (*(SA1.BWRAM + ((address & 0x7fff) - 0x6000)));
    case CMemory::MAP_BWRAM_BITMAP:
	address -= 0x600000;
	if (SA1.VirtualBitmapFormat == 2)
	    return ((Memory.BWRAM [(address >> 2) & 0x3ffff] >> ((address & 3) << 1)) & 3);
	else
	    return ((Memory.BWRAM [(address >> 1) & 0x3ffff] >> ((address & 1) << 2)) & 15);

    case CMemory::MAP_BWRAM_BITMAP2:
	address = (address & 0xffff) - 0x6000;
	if (SA1.VirtualBitmapFormat == 2)
	    return ((SA1.BWRAM [(address >> 2) & 0xffff] >> ((address & 3) << 1)) & 3);
	else
	    return ((SA1.BWRAM [(address >> 1) & 0xffff] >> ((address & 1) << 2)) & 15);

    default:
	return (0);
    }
}

void S9xSA1SetByte (uint8 byte, uint32 address)
{
    uint8 *Setaddress = SA1.WriteMap [(address >> 12) & 0xfff];

    if (Setaddress >= (uint8 *) CMemory::MAP_LAST)
    {
	*(Setaddress + (address & 0xffff)) = byte;
	return;
    }

    // CORREÇÃO: Cast para long para compatibilidade com 64-bits
    switch ((long) Setaddress)
    {
    case CMemory::MAP_PPU:
	S9xSetSA1 (byte, address & 0xffff);
	return;
    case CMemory::MAP_LOROM_SRAM:
    case CMemory::MAP_SA1RAM:
	*(Memory.SRAM + (address & 0xffff)) = byte;
	return;
    case CMemory::MAP_BWRAM:
	*(SA1.BWRAM + ((address & 0x7fff) - 0x6000)) = byte;
	return;
    case CMemory::MAP_BWRAM_BITMAP:
	address -= 0x600000;
	if (SA1.VirtualBitmapFormat == 2)
	{
	    uint8 *ptr = &Memory.BWRAM [(address >> 2) & 0x3ffff];
	    *ptr &= ~(3 << ((address & 3) << 1));
	    *ptr |= (byte & 3) << ((address & 3) << 1);
	}
	else
	{
	    uint8 *ptr = &Memory.BWRAM [(address >> 1) & 0x3ffff];
	    *ptr &= ~(15 << ((address & 1) << 2));
	    *ptr |= (byte & 15) << ((address & 1) << 2);
	}
	return;
    case CMemory::MAP_BWRAM_BITMAP2:
	address = (address & 0xffff) - 0x6000;
	if (SA1.VirtualBitmapFormat == 2)
	{
	    uint8 *ptr = &SA1.BWRAM [(address >> 2) & 0xffff];
	    *ptr &= ~(3 << ((address & 3) << 1));
	    *ptr |= (byte & 3) << ((address & 3) << 1);
	}
	else
	{
	    uint8 *ptr = &SA1.BWRAM [(address >> 1) & 0xffff];
	    *ptr &= ~(15 << ((address & 1) << 2));
	    *ptr |= (byte & 15) << ((address & 1) << 2);
	}
    }
}

void S9xSA1ExecuteDuringSleep ()
{
#if 0
    if (SA1.Executing)
    {
	while (SA1.Executing)
	{
	    S9xSA1MainLoop ();
	    
	    if (SA1.Flags & (IRQ_PENDING_FLAG | IRQ_FLAG))
		break;
	}
    }
#endif
}

void S9xSetSA1MemMap (uint32 which, uint8 map)
{
    int c;
    int start = which * 0x100 + 0xc00;
    int start2 = start + 0x800;

    if (which >= 2)
	start2 = 0x4000; // Not mapped

    switch (map)
    {
    case 0:	// ROM
	for (c = 0; c < 0x100; c += 16)
	{
	    SA1.Map [start + c] = SA1.Map [start2 + c] = &Memory.ROM [0];
	    SA1.WriteMap [start + c] = SA1.WriteMap [start2 + c] = (uint8 *) CMemory::MAP_NONE;
	}
	break;
    case 1:	// BWRAM
	for (c = 0; c < 0x100; c += 16)
	{
	    SA1.Map [start + c] = SA1.Map [start2 + c] = SA1.BWRAM;
	    SA1.WriteMap [start + c] = SA1.WriteMap [start2 + c] = SA1.BWRAM;
	}
	break;
    case 2:	// I-RAM
	for (c = 0; c < 0x100; c += 16)
	{
	    SA1.Map [start + c] = SA1.Map [start2 + c] = &Memory.FillRAM [0x3000];
	    SA1.WriteMap [start + c] = SA1.WriteMap [start2 + c] = &Memory.FillRAM [0x3000];
	}
	break;
    case 3:	// D-RAM
	for (c = 0; c < 0x100; c += 16)
	{
	    SA1.Map [start + c] = SA1.Map [start2 + c] = (uint8 *) CMemory::MAP_NONE;
	    SA1.WriteMap [start + c] = SA1.WriteMap [start2 + c] = (uint8 *) CMemory::MAP_NONE;
	}
	break;
    }
}

uint8 S9xGetSA1 (uint32 address)
{
//    printf ("R: %04x\n", address);
    switch (address)
    {
    case 0x2300:
	return ((uint8) ((Memory.FillRAM [0x2209] & 0x5f) | 
		 (CPU.IRQActive & (SA1_IRQ_SOURCE | SA1_DMA_IRQ_SOURCE)) |
		 (SA1.Waiting ? 0x20 : 0)));
    case 0x2301:
	return ((Memory.FillRAM [0x2200] & 0xf) |
		(Memory.FillRAM [0x2301] & 0xf0));
    case 0x2306:
	return ((uint8)  SA1.sum);
    case 0x2307:
	return ((uint8) (SA1.sum >> 8));
    case 0x2308:
	return ((uint8) (SA1.sum >> 16));
    case 0x2309:
	return ((uint8) (SA1.sum >> 24));
    case 0x230a:
	return ((uint8) (SA1.sum >> 32));
    case 0x230c:
	return (Memory.FillRAM [0x230c]);
    case 0x230d:
	{
	    uint8 byte = Memory.FillRAM [0x230d];

	    if (Memory.FillRAM [0x2258] & 0x80)
	    {
		S9xSA1ReadVariableLengthData (TRUE, FALSE);
	    }
	    return (byte);
	}
    }	
    return (Memory.FillRAM [address]);
}

void S9xSetSA1 (uint8 byte, uint32 address)
{
//    printf ("W: %02x -> %04x\n", byte, address);
    switch (address)
    {
    case 0x2200:
	SA1.Waiting = (byte & 0x60) != 0;
	if (!(byte & 0x20) && (Memory.FillRAM [0x2200] & 0x20))
	{
	    S9xSA1Reset ();
	}
	if (byte & 0x80)
	{
	    Memory.FillRAM [0x2301] |= 0x80;
	    if (Memory.FillRAM [0x220a] & 0x80)
	    {
		SA1.Flags |= IRQ_PENDING_FLAG;
		SA1.IRQActive |= SNES_IRQ_SOURCE;
		SA1.Executing = !SA1.Waiting;
	    }
	}
	if (byte & 0x10)
	{
	    Memory.FillRAM [0x2301] |= 0x10;
	    if (Memory.FillRAM [0x220a] & 0x10)
	    {
#ifdef DEBUGGER
		printf ("IRQ to SA-1\n");
#endif
	    }
	}
	break;

    case 0x2201:
	if (((byte ^ Memory.FillRAM [0x2201]) & 0x80) &&
	    (Memory.FillRAM [0x2300] & byte & 0x80))
	{
	    S9xSetIRQ (SA1_IRQ_SOURCE);
	}
	if (((byte ^ Memory.FillRAM [0x2201]) & 0x20) &&
	    (Memory.FillRAM [0x2300] & byte & 0x20))
	{
	    S9xSetIRQ (SA1_DMA_IRQ_SOURCE);
	}
	break;
    case 0x2202:
	if (byte & 0x80)
	{
	    Memory.FillRAM [0x2300] &= ~0x80;
	    S9xClearIRQ (SA1_IRQ_SOURCE);
	}
	if (byte & 0x20)
	{
	    Memory.FillRAM [0x2300] &= ~0x20;
	    S9xClearIRQ (SA1_DMA_IRQ_SOURCE);
	}
	break;
    case 0x2203:
//	printf ("SA1 reset vector: %04x\n", byte | (Memory.FillRAM [0x2204] << 8));
	break;
    case 0x2204:
//	printf ("SA1 reset vector: %04x\n", (byte << 8) | Memory.FillRAM [0x2203]);
	break;

    case 0x2205:
//	printf ("SA1 NMI vector: %04x\n", byte | (Memory.FillRAM [0x2206] << 8));
	break;
    case 0x2206:
//	printf ("SA1 NMI vector: %04x\n", (byte << 8) | Memory.FillRAM [0x2205]);
	break;

    case 0x2207:
//	printf ("SA1 IRQ vector: %04x\n", byte | (Memory.FillRAM [0x2208] << 8));
	break;
    case 0x2208:
//	printf ("SA1 IRQ vector: %04x\n", (byte << 8) | Memory.FillRAM [0x2207]);
	break;

    case 0x2209:
	if (byte & 0x80)
	    Memory.FillRAM [0x2300] |= 0x80;
	if (byte & 0x20)
	    Memory.FillRAM [0x2300] |= 0x20;
	break;
    case 0x220a:
	if (((byte ^ Memory.FillRAM [0x220a]) & 0x80) &&
	    (Memory.FillRAM [0x2301] & byte & 0x80))
	{
	    SA1.Flags |= IRQ_PENDING_FLAG;
	    SA1.IRQActive |= SNES_IRQ_SOURCE;
	    SA1.Executing = !SA1.Waiting;
	}
	if (((byte ^ Memory.FillRAM [0x220a]) & 0x40) &&
	    (Memory.FillRAM [0x2301] & byte & 0x40))
	{
	    SA1.Flags |= IRQ_PENDING_FLAG;
	    SA1.IRQActive |= TIMER_IRQ_SOURCE;
	    SA1.Executing = !SA1.Waiting;
	}
	if (((byte ^ Memory.FillRAM [0x220a]) & 0x20) &&
	    (Memory.FillRAM [0x2301] & byte & 0x20))
	{
	    SA1.Flags |= IRQ_PENDING_FLAG;
	    SA1.IRQActive |= DMA_IRQ_SOURCE;
	    SA1.Executing = !SA1.Waiting;
	}
	if (((byte ^ Memory.FillRAM [0x220a]) & 0x10) &&
	    (Memory.FillRAM [0x2301] & byte & 0x10))
	{
#ifdef DEBUGGER
	    printf ("IRQ to SA-1\n");
#endif
	}
	break;
    case 0x220b:
	if (byte & 0x80)
	{
	    SA1.IRQActive &= ~SNES_IRQ_SOURCE;
	    Memory.FillRAM [0x2301] &= ~0x80;
	}
	if (byte & 0x40)
	{
	    SA1.IRQActive &= ~TIMER_IRQ_SOURCE;
	    Memory.FillRAM [0x2301] &= ~0x40;
	}
	if (byte & 0x20)
	{
	    SA1.IRQActive &= ~DMA_IRQ_SOURCE;
	    Memory.FillRAM [0x2301] &= ~0x20;
	}
	if (byte & 0x10)
	{
	    // Clear IRQ from SA-1 to SA-1 (?)
	    Memory.FillRAM [0x2301] &= ~0x10;
	}
	if (!SA1.IRQActive)
	    SA1.Flags &= ~IRQ_PENDING_FLAG;
	break;
    case 0x220c:
//	printf ("SNES NMI vector: %04x\n", byte | (Memory.FillRAM [0x220d] << 8));
	break;
    case 0x220d:
//	printf ("SNES NMI vector: %04x\n", (byte << 8) | Memory.FillRAM [0x220c]);
	break;

    case 0x220e:
//	printf ("SNES IRQ vector: %04x\n", byte | (Memory.FillRAM [0x220f] << 8));
	break;
    case 0x220f:
//	printf ("SNES IRQ vector: %04x\n", (byte << 8) | Memory.FillRAM [0x220e]);
	break;

    case 0x2210:
#if 0
	printf ("Timer %s\n", (byte & 0x80) ? "linear" : "HV");
	printf ("Timer H-IRQ %s\n", (byte & 1) ? "enabled" : "disabled");
	printf ("Timer V-IRQ %s\n", (byte & 2) ? "enabled" : "disabled");
#endif
	break;
    case 0x2211:
	printf ("Timer reset\n");
	break;
    case 0x2212:
	printf ("H-Timer %04x\n", byte | (Memory.FillRAM [0x2213] << 8));
	break;
    case 0x2213:
	printf ("H-Timer %04x\n", (byte << 8) | Memory.FillRAM [0x2212]);
	break;
    case 0x2214:
	printf ("V-Timer %04x\n", byte | (Memory.FillRAM [0x2215] << 8));
	break;
    case 0x2215:
	printf ("V-Timer %04x\n", (byte << 8) | Memory.FillRAM [0x2214]);
	break;
    case 0x2220:
    case 0x2221:
    case 0x2222:
    case 0x2223:
	S9xSetSA1MemMap (address - 0x2220, byte);
	break;
    case 0x2224:
//	printf ("BW-RAM image SNES %02x -> 0x6000\n", byte);
	break;
    case 0x2225:
//	printf ("BW-RAM image SA1 %02x -> 0x6000 (%02x)\n", byte, Memory.FillRAM [address]);
	if (byte != Memory.FillRAM [address])
	    S9xSA1SetBWRAMMemMap (byte);
	break;
    case 0x2226:
//	printf ("BW-RAM SNES write %s\n", (byte & 0x80) ? "enabled" : "disabled");
	break;
    case 0x2227:
//	printf ("BW-RAM SA1 write %s\n", (byte & 0x80) ? "enabled" : "disabled");
	break;

    case 0x2228:
	Memory.FillRAM [0x2228] = byte;
	break;

    case 0x2229:
	Memory.FillRAM [0x2229] = byte;
	if (byte & 0x80)
	    Memory.FillRAM [0x2300] |= 0x80;
	break;

    case 0x222a:
    case 0x222b:
    case 0x222c:
    case 0x222d:
    case 0x222e:
	break;

    case 0x2230:
	break;
    case 0x2231:
	if (byte & 0x80)
    {
	    // SA1.Completed = FALSE;
    }
	break;
    case 0x2232:
    case 0x2233:
    case 0x2234:
	Memory.FillRAM [address] = byte;
	break;
    case 0x2235:
	Memory.FillRAM [address] = byte;
	break;
    case 0x2236:
	Memory.FillRAM [address] = byte;
	if ((Memory.FillRAM [0x2230] & 0xa4) == 0x80)
	{
	    // Normal DMA to I-RAM
	    S9xSA1DMA ();
	}
	else
	if ((Memory.FillRAM [0x2230] & 0xb0) == 0xb0)
	{
	    Memory.FillRAM [0x2300] |= 0x20;
	    if (Memory.FillRAM [0x2201] & 0x20)
		S9xSetIRQ (SA1_DMA_IRQ_SOURCE);
	    // SA1.Completed = TRUE;
	}
	break;
    case 0x2237:
	Memory.FillRAM [address] = byte;
	if ((Memory.FillRAM [0x2230] & 0xa4) == 0x84)
	{
	    // Normal DMA to BW-RAM
	    S9xSA1DMA ();
	}
	else
	if ((Memory.FillRAM [0x2230] & 0xb0) == 0xb0)
	{
	    Memory.FillRAM [0x2300] |= 0x20;
	    if (Memory.FillRAM [0x2201] & 0x20)
		S9xSetIRQ (SA1_DMA_IRQ_SOURCE);
	    // SA1.Completed = TRUE;
	}
	break;
    case 0x2238:
    case 0x2239:
	Memory.FillRAM [address] = byte;
	break;
    case 0x223f:
	SA1.VirtualBitmapFormat = (byte & 0x80) >> 7;
	break;

    case 0x2240:    case 0x2241:    case 0x2242:    case 0x2243:
    case 0x2244:    case 0x2245:    case 0x2246:    case 0x2247:
    case 0x2248:    case 0x2249:    case 0x224a:    case 0x224b:
    case 0x224c:    case 0x224d:    case 0x224e:
	Memory.FillRAM [address] = byte;
	break;

    case 0x2250:
	if (byte & 2)
	    SA1.sum = 0;
	SA1.arithmetic_op = byte & 3;
	break;

    case 0x2251:
	SA1.op1 = (SA1.op1 & 0xff00) | byte;
	break;
    case 0x2252:
	SA1.op1 = (SA1.op1 & 0x00ff) | (byte << 8);
	break;
    case 0x2253:
	SA1.op2 = (SA1.op2 & 0xff00) | byte;
	break;
    case 0x2254:
	SA1.op2 = (SA1.op2 & 0x00ff) | (byte << 8);
	switch (SA1.arithmetic_op)
	{
	case 0:	// multiply
	    SA1.sum = SA1.op1 * SA1.op2;
	    break;
	case 1: // divide
	    if (SA1.op2 == 0)
		SA1.sum = 0;
	    else
	    {
		SA1.sum = (SA1.op1 / (int) ((uint16) SA1.op2)) |
			  ((SA1.op1 % (int) ((uint16) SA1.op2)) << 16);
	    }
	    break;
	case 2:
	default: // cumulative sum
	    SA1.sum += SA1.op1 * SA1.op2;
	    if (SA1.sum & ((int64) 0xffffff << 32))
		SA1.overflow = TRUE;
	    break;
	}
	break;
    case 0x2258:    // Variable bit-field length/auto inc/start
	Memory.FillRAM [0x2258] = byte;
	S9xSA1ReadVariableLengthData (TRUE, FALSE);
	return;
    case 0x2259:
    case 0x225a:
    case 0x225b:
	Memory.FillRAM [address] = byte;
	break;
    }
    Memory.FillRAM [address] = byte;
}

static void S9xSA1ReadVariableLengthData (bool8 inc, bool8 no_shift)
{
    uint32 addr =  Memory.FillRAM [0x2259] |
		  (Memory.FillRAM [0x225a] << 8) |
		  (Memory.FillRAM [0x225b] << 16);
    uint8 shift = Memory.FillRAM [0x2258] & 15;

    if (no_shift)
	shift = 0;
    else
    if (shift == 0)
	shift = 16;

    uint8 s = shift + (Memory.FillRAM [0x2258] >> 4);
    if (s > 16)
	s = 16;

    uint32 data = S9xSA1GetByte (addr) |
		  (S9xSA1GetByte (addr + 1) << 8) |
		  (S9xSA1GetByte (addr + 2) << 16);

    data >>= (Memory.FillRAM [0x2258] >> 4);
    if (shift < 16)
	data &= (1 << shift) - 1;
    data &= (1 << s) - 1;
    Memory.FillRAM [0x230c] = (uint8) data;
    Memory.FillRAM [0x230d] = (uint8) (data >> 8);
    if (inc)
    {
	Memory.FillRAM [0x2258] = (Memory.FillRAM [0x2258] & 15) |
				  ((Memory.FillRAM [0x2258] & 0xf0) + (shift << 4));
	if ((Memory.FillRAM [0x2258] & 0xf0) >= 0x100)
	{
	    Memory.FillRAM [0x2258] &= 15;
	    addr++;
	    Memory.FillRAM [0x2259] = (uint8) addr;
	    Memory.FillRAM [0x225a] = (uint8) (addr >> 8);
	    Memory.FillRAM [0x225b] = (uint8) (addr >> 16);
	}
    }
}

static void S9xSA1DMA ()
{
    uint32 src =  Memory.FillRAM [0x2232] |
		 (Memory.FillRAM [0x2233] << 8) |
		 (Memory.FillRAM [0x2234] << 16);
    uint32 dst =  Memory.FillRAM [0x2235] |
		 (Memory.FillRAM [0x2236] << 8) |
		 (Memory.FillRAM [0x2237] << 16);
    int len =  Memory.FillRAM [0x2238] |
	      (Memory.FillRAM [0x2239] << 8);

    uint8 *s;
    uint8 *d;

    switch (Memory.FillRAM [0x2230] & 3)
    {
    case 0: // ROM
	s = SA1.Map [(src >> 12) & 0xfff];
	if (s >= (uint8 *) CMemory::MAP_LAST)
	    s += (src & 0xffff);
	else
	    s = NULL;
	break;
    case 1: // BW-RAM
	s = SA1.BWRAM + ((src & 0x7fff) - 0x6000);
	break;
    default:
    case 2: // I-RAM
	s = &Memory.FillRAM [0x3000 + (src & 0x7ff)];
	break;
    }

    if (Memory.FillRAM [0x2230] & 4)
    {
	dst &= 0x7fff;
	if (dst < 0x6000)
	    d = NULL;
	else
	    d = SA1.BWRAM + (dst - 0x6000);
    }
    else
    {
	dst &= 0x7ff;
	d = &Memory.FillRAM [0x3000 + dst];
    }

    if (s && d)
    {
	uint8 *open = SA1.BWRAM + ((src & 0x7fff) - 0x6000 + len);
	// OPEN_BUS simulation...
	if (open >= SA1.BWRAM && open < SA1.BWRAM + 0x2000)
	    Memory.FillRAM [0x230e] = *open;

	while (len)
	{
	    *d++ = *s++;
	    len--;
	}
    }
    Memory.FillRAM [0x2300] |= 0x20;
    if (Memory.FillRAM [0x2201] & 0x20)
	S9xSetIRQ (SA1_DMA_IRQ_SOURCE);
    // SA1.Completed = TRUE;
}

void S9xSA1CharConv2 ()
{
    uint32 dest = Memory.FillRAM [0x2235] | (Memory.FillRAM [0x2236] << 8);
    uint32 offset = (SA1.VirtualBitmapFormat == 2) ? (dest & 3) : (dest & 1);
    int sym = (SA1.VirtualBitmapFormat == 2) ? 4 : 2;
    int bytes = (SA1.VirtualBitmapFormat == 2) ? 16 : 8;
    uint8 *d = &SA1.BWRAM [dest >> sym];

    for (int i = 0; i < bytes; i++)
    {
	uint8 b = Memory.FillRAM [0x2240 + i];
	int bits = sym * 2;
	for (int j = 0; j < 8; j += sym)
	{
	    *d &= ~(15 << bits);
	    *d |= (b & 3) << bits;
	    bits -= sym;
	    b >>= 2;
	}
	d++;
    }
}
