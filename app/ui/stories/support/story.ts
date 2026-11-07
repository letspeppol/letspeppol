import type { AureliaStoryResult } from '@aurelia/storybook';
import type { Args, ComponentAnnotations, Renderer, StoryAnnotations } from 'storybook/internal/types';

export interface AureliaRenderer extends Renderer {
  component: unknown;
  storyResult: AureliaStoryResult;
  canvasElement: HTMLElement;
}

export type Meta<TArgs = Args> = ComponentAnnotations<AureliaRenderer, TArgs>;
export type Story<TArgs = Args> = StoryAnnotations<AureliaRenderer, TArgs>;

export function template(markup: string, extra: Omit<AureliaStoryResult, 'template'> = {}): () => AureliaStoryResult {
  return () => ({ template: markup, ...extra });
}
