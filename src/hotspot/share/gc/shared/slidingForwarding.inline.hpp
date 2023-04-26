/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifndef SHARE_GC_SHARED_SLIDINGFORWARDING_INLINE_HPP
#define SHARE_GC_SHARED_SLIDINGFORWARDING_INLINE_HPP

#include "gc/shared/slidingForwarding.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"

#ifdef _LP64
size_t SlidingForwarding::region_index_containing(HeapWord* addr) const {
  assert(addr >= _heap_start, "sanity: addr: " PTR_FORMAT " heap base: " PTR_FORMAT, p2i(addr), p2i(_heap_start));
  size_t index = ((size_t) (addr - _heap_start)) >> _region_size_words_shift;
  assert(index < _num_regions, "Region index is in bounds: " PTR_FORMAT, p2i(addr));
  return index;
}

bool SlidingForwarding::region_contains(HeapWord* region_base, HeapWord* addr) const {
  return uintptr_t(addr - region_base) < (1u << _region_size_words_shift);
}


uintptr_t SlidingForwarding::encode_forwarding(HeapWord* original, HeapWord* target) {
  size_t orig_idx = region_index_containing(original);
  size_t base_table_idx = orig_idx * 2;
  size_t target_idx = region_index_containing(target);
  HeapWord* encode_base;
  uintptr_t region_idx;
  for (region_idx = 0; region_idx < NUM_REGIONS; region_idx++) {
    encode_base = _target_base_table[base_table_idx + region_idx];
    if (encode_base == UNUSED_BASE) {
      encode_base = _heap_start + target_idx * (1 << _region_size_words_shift);
      _target_base_table[base_table_idx + region_idx] = encode_base;
      break;
    } else if (region_contains(encode_base, target)) {
      break;
    }
  }
  if (region_idx >= NUM_REGIONS) {
    assert(UseG1GC, "Only happens with G1 serial compaction");
    return 1 << FALLBACK_SHIFT | markWord::marked_value;
  }
  assert(region_idx < NUM_REGIONS, "need to have found an encoding base");
  assert(target >= encode_base, "target must be above encode base, target:" PTR_FORMAT ", encoded_base: " PTR_FORMAT ",  target_idx: " SIZE_FORMAT ", heap start: " PTR_FORMAT ", region_idx: " INTPTR_FORMAT,
         p2i(target), p2i(encode_base), target_idx, p2i(_heap_start), region_idx);
  assert(region_contains(encode_base, target), "region must contain target: original: " PTR_FORMAT ", target: " PTR_FORMAT ", encode_base: " PTR_FORMAT ", region_idx: " INTPTR_FORMAT, p2i(original), p2i(target), p2i(encode_base), region_idx);
  uintptr_t encoded = (((uintptr_t)(target - encode_base)) << COMPRESSED_BITS_SHIFT) |
                      (region_idx << REGION_SHIFT) | markWord::marked_value;
  assert(target == decode_forwarding(original, encoded), "must be reversible");
  return encoded;
}

HeapWord* SlidingForwarding::decode_forwarding(HeapWord* original, uintptr_t encoded) const {
  assert((encoded & markWord::marked_value) == markWord::marked_value, "must be marked as forwarded");
  size_t orig_idx = region_index_containing(original);
  size_t region_idx = (encoded >> REGION_SHIFT) & right_n_bits(REGION_BITS);
  size_t base_table_idx = orig_idx * 2 + region_idx;
  HeapWord* decoded = _target_base_table[base_table_idx] + (encoded >> COMPRESSED_BITS_SHIFT);
  assert(decoded >= _heap_start, "must be above heap start, encoded: " INTPTR_FORMAT ", region_idx: " SIZE_FORMAT ", base: " PTR_FORMAT, encoded, region_idx, p2i(_target_base_table[base_table_idx]));
  return decoded;
}
#endif

void SlidingForwarding::forward_to(oop original, oop target) {
#ifdef _LP64
  markWord header = original->mark();
  if (header.has_displaced_mark_helper()) {
    header = header.displaced_mark_helper();
  }
  HeapWord* from = cast_from_oop<HeapWord*>(original);
  HeapWord* to   = cast_from_oop<HeapWord*>(target);
  uintptr_t encoded = encode_forwarding(from, to);
  header = markWord((header.value() & ~MARK_LOWER_HALF_MASK) | encoded);
  original->set_mark(header);
  if ((encoded & FALLBACK_MASK) != 0) {
    //tty->print_cr("fallback forward: " PTR_FORMAT " to: " PTR_FORMAT, p2i(from), p2i(to));
    fallback_forward_to(from, to);
    return;
  }
#else
  original->forward_to(target);
#endif
}

oop SlidingForwarding::forwardee(oop original) const {
#ifdef _LP64
  markWord header = original->mark();
  if ((header.value() & FALLBACK_MASK) != 0) {
    //tty->print_cr("fallback forwardee mark: " INTPTR_FORMAT, header.value());
    HeapWord* from = cast_from_oop<HeapWord*>(original);
    HeapWord* to = fallback_forwardee(from);
    //tty->print_cr("fallback forwardee, from: " PTR_FORMAT " to: " PTR_FORMAT, p2i(from), p2i(to));
    return cast_to_oop(to);
  }
  uintptr_t encoded = header.value() & MARK_LOWER_HALF_MASK;
  HeapWord* forwardee = decode_forwarding(cast_from_oop<HeapWord*>(original), encoded);
  return cast_to_oop(forwardee);
#else
  return original->forwardee();
#endif
}

#endif // SHARE_GC_SHARED_SLIDINGFORWARDING_INLINE_HPP
