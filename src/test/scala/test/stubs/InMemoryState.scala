package es.eriktorr
package test.stubs

trait InMemoryState[Self <: InMemoryState[Self, A], A]:
  def value: A
  def set(newValue: A): Self
