package com.airbnb.lottie.model.animatable;

import com.airbnb.lottie.animation.Keyframe;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseAnimatableValue<V, O> implements AnimatableValue<V, O> {
  final List<Keyframe<V>> keyframes;

  /**
   * Create a default static animatable path.
   */
  BaseAnimatableValue(V value) {
    this(Collections.singletonList(new Keyframe<>(value)));
  }

  BaseAnimatableValue(List<Keyframe<V>> keyframes) {
    if (keyframes.isEmpty()) {
      throw new IllegalStateException("There are no keyframes");
    }
    this.keyframes = keyframes;
  }

  @Override public String toString() {
    final StringBuilder sb = new StringBuilder();
    if (!keyframes.isEmpty()) {
      sb.append("values=").append(Arrays.toString(keyframes.toArray()));
    }
    return sb.toString();
  }
}
