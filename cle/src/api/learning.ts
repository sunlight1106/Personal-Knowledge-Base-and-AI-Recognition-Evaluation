import { request } from './client'
export interface LearningMetrics { answers:number; correct:number; words:number; cards:number; notes:number; seconds:number }
export interface LearningSummary { userId:number; zoneId:string; total:LearningMetrics; days:{date:string;metrics:LearningMetrics}[]; activeDays:number }
export interface LearningToday { statistics:LearningSummary; notes:{id:string;title:string;url:string}[]; dueCards:number; dueWords:number; unfinishedWords:number }
export const learningApi={
  statistics:(days=30,userId?:number,signal?:AbortSignal)=>request<LearningSummary>(`/learning/${userId?'users/'+userId+'/':''}statistics?days=${days}`,{signal}),
  today:(signal?:AbortSignal)=>request<LearningToday>('/learning/today',{signal}),
  pulse:(context:string,seconds:number)=>request<void>('/learning/pulse',{method:'POST',body:JSON.stringify({context,seconds})}),
}
