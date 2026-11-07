import { Registration } from 'aurelia';
import type { IRegistration, Key } from '@aurelia/kernel';

const warned = new Set<string>();

export function stub<T extends object>(name: string, members: Partial<Record<keyof T, unknown>> & Record<string, unknown>): T {
  return new Proxy(members, {
    get(target, property, receiver) {
      if (property in target || typeof property === 'symbol' || property === 'then' || property.startsWith('$') || property.startsWith('__')) {
        return Reflect.get(target, property, receiver);
      }
      const label = `${name}.${String(property)}`;
      if (!warned.has(label)) {
        warned.add(label);
        console.warn(`[storybook mock] ${label} is not stubbed`);
      }
      return undefined;
    },
  }) as T;
}

export function provide<T>(key: Key, instance: T): IRegistration<T> {
  return Registration.instance(key, instance);
}

export function resolved<T>(value: T, delayMs = 0): () => Promise<T> {
  return () => new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), delayMs));
}

export function pending<T>(): () => Promise<T> {
  return () => new Promise<T>(() => undefined);
}

export function rejected(status = 500): () => Promise<never> {
  return () => Promise.reject(new Response(null, { status }));
}
