package com.identifiant.ordoread.di

import android.content.Context
import com.identifiant.ordoread.data.ai.SmartPrescriptionAnalyzer
import com.identifiant.ordoread.data.device.MLKitTextRecognitionService
import com.identifiant.ordoread.domain.ai.PrescriptionAnalyzer
import com.identifiant.ordoread.domain.device.TextRecognitionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {

    @Provides
    @Singleton
    fun provideTextRecognitionService(): TextRecognitionService {
        return MLKitTextRecognitionService()
    }

    @Provides
    @Singleton
    fun providePrescriptionAnalyzer(@ApplicationContext context: Context): PrescriptionAnalyzer {
        return SmartPrescriptionAnalyzer(context)
    }
}
