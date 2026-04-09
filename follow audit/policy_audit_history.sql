-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 02:28 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `policy_audit_history`
--

CREATE TABLE `policy_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `policy_audit_history`
--

INSERT INTO `policy_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(1, 133, 'Policy', 'Details', 'Updated', 'Primary Name', 'PO1(one)', 'PO1(one)2', 'Alice Smith', '2025-10-30 12:10:59', '2025-10-30 12:10:59'),
(1, 134, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Policy Owner', 'Alice Smith', '2025-10-30 12:11:34', '2025-10-30 12:11:34'),
(1, 135, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'Alice Smith', '2025-10-30 12:11:34', '2025-10-30 12:11:34'),
(1, 136, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:11:34', '2025-10-30 12:11:34'),
(25, 20, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'سيبسيب', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 21, 'Policy', 'Details', 'Added', 'Description', NULL, 'dsfsdfg', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 22, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'sdfsd', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 23, 'Policy', 'Details', 'Added', 'Parent Policy', NULL, 'asdasd', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 24, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 25, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 26, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 27, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Viewing 1', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(25, 28, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'System', '2025-10-22 16:18:35', '2025-10-22 16:18:35'),
(26, 29, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'dfgdfg', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 30, 'Policy', 'Details', 'Added', 'Description', NULL, 'dfg', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 31, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'dfg', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 32, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 33, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 34, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 35, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Viewing 1', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 36, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'System', '2025-10-23 10:23:22', '2025-10-23 10:23:22'),
(26, 37, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'System', '2025-10-23 10:23:23', '2025-10-23 10:23:23'),
(26, 38, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-10-23 10:23:23', '2025-10-23 10:23:23'),
(26, 39, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'System', '2025-10-23 10:23:23', '2025-10-23 10:23:23'),
(27, 40, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'بيل', 'System', '2025-10-23 10:32:05', '2025-10-23 10:32:05'),
(27, 41, 'Policy', 'Details', 'Added', 'Description', NULL, 'يبليبل', 'System', '2025-10-23 10:32:05', '2025-10-23 10:32:05'),
(27, 42, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-23 10:32:05', '2025-10-23 10:32:05'),
(27, 43, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'System', '2025-10-23 10:32:05', '2025-10-23 10:32:05'),
(27, 44, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'System', '2025-10-23 10:32:05', '2025-10-23 10:32:05'),
(27, 45, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Viewing 1', 'System', '2025-10-23 10:32:05', '2025-10-23 10:32:05'),
(27, 46, 'Policy', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'System', '2025-10-23 10:32:06', '2025-10-23 10:32:06'),
(27, 47, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'System', '2025-10-23 10:32:06', '2025-10-23 10:32:06'),
(27, 48, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-10-23 10:32:06', '2025-10-23 10:32:06'),
(27, 49, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'System', '2025-10-23 10:32:06', '2025-10-23 10:32:06'),
(28, 50, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'gdfhfgh', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 51, 'Policy', 'Details', 'Added', 'Description', NULL, 'dfgdf', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 52, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'hfgh', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 53, 'Policy', 'Details', 'Added', 'Parent Policy', NULL, 'ghfghfgh', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 54, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 55, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 56, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 57, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Viewing 1', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 58, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 59, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 60, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(28, 61, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:30:51', '2025-10-23 16:30:51'),
(29, 62, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'fjhjg', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 63, 'Policy', 'Details', 'Added', 'Description', NULL, 'jgh', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 64, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'ghj', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 65, 'Policy', 'Details', 'Added', 'Parent Policy', NULL, 'dfgdfg', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 66, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 67, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 68, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 69, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Viewing 1', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 70, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 71, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 72, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 73, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:38:27', '2025-10-23 16:38:27'),
(29, 115, 'Policy', 'Details', 'Updated', 'Parent Policy', 'dfgdfg', NULL, 'Alice Smith', '2025-10-29 16:43:35', '2025-10-29 16:43:35'),
(29, 116, 'Policy', 'Details', 'Updated', 'Internal', 'Yes', 'No', 'Alice Smith', '2025-10-29 16:43:35', '2025-10-29 16:43:35'),
(29, 117, 'Policy', 'Details', 'Updated', 'URL', NULL, '', 'Alice Smith', '2025-10-29 16:43:35', '2025-10-29 16:43:35'),
(29, 118, 'Policy', 'Details', 'Updated', 'Internal', 'No', 'Yes', 'Alice Smith', '2025-10-29 16:44:03', '2025-10-29 16:44:03'),
(29, 119, 'Policy', 'Details', 'Updated', 'URL', NULL, '', 'Alice Smith', '2025-10-29 16:44:03', '2025-10-29 16:44:03'),
(29, 120, 'Policy', 'Details', 'Updated', 'Internal', 'Yes', 'No', 'Alice Smith', '2025-10-29 16:46:18', '2025-10-29 16:46:18'),
(30, 74, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'sdf', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 75, 'Policy', 'Details', 'Added', 'Description', NULL, 'sdfsd', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 76, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'sdfs', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 77, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 78, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 79, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 80, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Viewing 1', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 81, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:53:32', '2025-10-26 15:53:32'),
(30, 82, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-26 15:53:33', '2025-10-26 15:53:33'),
(30, 83, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:53:33', '2025-10-26 15:53:33'),
(30, 84, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:53:33', '2025-10-26 15:53:33'),
(30, 96, 'Policy', 'Details', 'Updated', 'Internal', 'Yes', NULL, 'Alice Smith', '2025-10-29 16:29:10', '2025-10-29 16:29:10'),
(30, 97, 'Policy', 'Details', 'Updated', 'Internal', 'No', NULL, 'Alice Smith', '2025-10-29 16:29:16', '2025-10-29 16:29:16'),
(30, 98, 'Policy', 'Details', 'Updated', 'Internal', 'No', NULL, 'Alice Smith', '2025-10-29 16:29:30', '2025-10-29 16:29:30'),
(31, 85, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'test100', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 86, 'Policy', 'Details', 'Added', 'Description', NULL, 'ds', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 87, 'Policy', 'Details', 'Added', 'Parent Policy', NULL, 'dewr', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 88, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 89, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 90, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 91, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Viewing 1', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 92, 'Policy', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 93, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 94, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(31, 95, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-29 16:18:22', '2025-10-29 16:18:22'),
(32, 99, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'لاتلاتلات', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 100, 'Policy', 'Details', 'Added', 'Description', NULL, 'تبتبل', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 101, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'لاتبلتبلا', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 102, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 103, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 104, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 105, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 106, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 107, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 108, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 109, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 16:34:23', '2025-10-29 16:34:23'),
(32, 110, 'Policy', 'Details', 'Updated', 'Primary Name', 'لاتلاتلات', 'لاتلاتلات2', 'Alice Smith', '2025-10-29 16:34:58', '2025-10-29 16:34:58'),
(32, 111, 'Policy', 'Details', 'Updated', 'Internal', 'Yes', NULL, 'Alice Smith', '2025-10-29 16:34:58', '2025-10-29 16:34:58'),
(32, 112, 'Policy', 'Details', 'Updated', 'Internal', 'No', NULL, 'Alice Smith', '2025-10-29 16:35:16', '2025-10-29 16:35:16'),
(32, 113, 'Policy', 'Details', 'Updated', 'Internal', 'No', NULL, 'Alice Smith', '2025-10-29 16:37:51', '2025-10-29 16:37:51'),
(32, 114, 'Policy', 'Details', 'Updated', 'Internal', 'No', NULL, 'Alice Smith', '2025-10-29 16:38:01', '2025-10-29 16:38:01'),
(33, 121, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'name', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 122, 'Policy', 'Details', 'Added', 'Description', NULL, 'Description', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 123, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 124, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 125, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 126, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 127, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 128, 'Policy', 'Details', 'Added', 'Internal', NULL, 'Yes', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 129, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 130, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 131, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 11:22:44', '2025-10-30 11:22:44'),
(33, 132, 'Policy', 'Details', 'Updated', 'Internal', 'Yes', 'No', 'Alice Smith', '2025-10-30 11:23:04', '2025-10-30 11:23:04'),
(34, 137, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'ettgerg', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 138, 'Policy', 'Details', 'Added', 'Description', NULL, 'dfgsgsd', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 139, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'gdfgd', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 140, 'Policy', 'Details', 'Added', 'Parent Policy', NULL, 'asdasd', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 141, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 142, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 143, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 144, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 145, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 146, 'Policy', 'Details', 'Added', 'Internal', NULL, 'Yes', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 147, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 148, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(34, 149, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:13:15', '2025-10-30 12:13:15'),
(35, 150, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'fsdfsdf', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 151, 'Policy', 'Details', 'Added', 'Description', NULL, 'sdf', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 152, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 153, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 154, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 155, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 156, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 157, 'Policy', 'Details', 'Added', 'Internal', NULL, 'No', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 158, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 159, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(35, 160, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:13:31', '2025-10-30 12:13:31'),
(36, 161, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'sdfgsdgs', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 162, 'Policy', 'Details', 'Added', 'Description', NULL, 'dfgsdfgfg', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 163, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'sdfgsdfg', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 164, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 165, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 166, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 167, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 168, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 169, 'Policy', 'Details', 'Added', 'Internal', NULL, 'No', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 170, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 171, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:13:47', '2025-10-30 12:13:47'),
(36, 172, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:13:48', '2025-10-30 12:13:48'),
(37, 173, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'dfghdfgh', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 174, 'Policy', 'Details', 'Added', 'Description', NULL, 'dfghdfghdfgh', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 175, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'ghdfg', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 176, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 177, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 178, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 179, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 180, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 181, 'Policy', 'Details', 'Added', 'Internal', NULL, 'No', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 182, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 183, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(37, 184, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 12:23:40', '2025-10-30 12:23:40'),
(38, 185, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'jkjhjh', 'admin admin', '2025-10-30 16:18:04', '2025-10-30 16:18:04'),
(38, 186, 'Policy', 'Details', 'Added', 'Description', NULL, 'kljkjhjk', 'admin admin', '2025-10-30 16:18:04', '2025-10-30 16:18:04'),
(38, 187, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 188, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 189, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 190, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 191, 'Policy', 'Details', 'Added', 'Created By', NULL, 'admin admin', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 192, 'Policy', 'Details', 'Added', 'Internal', NULL, 'No', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 193, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 194, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(38, 195, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'admin admin', 'admin admin', '2025-10-30 16:18:05', '2025-10-30 16:18:05'),
(39, 196, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'ابلابليا', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 197, 'Policy', 'Details', 'Added', 'Description', NULL, 'يبلايبلا', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 198, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'POL-1761830766712', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 199, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 200, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 201, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 202, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 203, 'Policy', 'Details', 'Added', 'Created By', NULL, 'admin admin', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 204, 'Policy', 'Details', 'Added', 'Internal', NULL, 'Yes', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 205, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 206, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(39, 207, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'admin admin', 'admin admin', '2025-10-30 16:26:07', '2025-10-30 16:26:07'),
(40, 208, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'dfgdfgsdfg', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 209, 'Policy', 'Details', 'Added', 'Description', NULL, 'sdfgsdfg', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 210, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'POL-40', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 211, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Active', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 212, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'on', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 213, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't1', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 214, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Public', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 215, 'Policy', 'Details', 'Added', 'Created By', NULL, 'admin admin', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 216, 'Policy', 'Details', 'Added', 'Internal', NULL, 'No', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 217, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 218, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 219, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'admin admin', 'admin admin', '2025-10-30 16:31:28', '2025-10-30 16:31:28'),
(40, 220, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Charlie Brown', 'Alice Smith', '2025-11-02 10:54:13', '2025-11-02 10:54:13'),
(40, 221, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Policy Steward', 'Alice Smith', '2025-11-02 10:54:13', '2025-11-02 10:54:13'),
(40, 222, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-02 10:54:13', '2025-11-02 10:54:13'),
(41, 223, 'Policy', 'Details', 'Added', 'Primary Name', NULL, 'policy_test4', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 224, 'Policy', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 225, 'Policy', 'Details', 'Added', 'Reference Number', NULL, 'POL-41', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 226, 'Policy', 'Details', 'Added', 'Parent Policy', NULL, 'test3', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 227, 'Policy', 'Details', 'Status Change', 'Status', NULL, 'Inactive', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 228, 'Policy', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'off', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 229, 'Policy', 'Details', 'Added', 'Policy Type', NULL, 't2', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 230, 'Policy', 'Details', 'Added', 'Is Public', NULL, 'Private', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 231, 'Policy', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 232, 'Policy', 'Details', 'Added', 'Internal', NULL, 'Yes', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 233, 'Policy', 'Details', 'Added', 'URL', NULL, 'https://onlinehelp.informatica.com/axon/7.0/en/index.htm#page/axon-data-governance-administrator-guide/Segmentation.html', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 234, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 235, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 236, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:27:00', '2025-11-09 13:27:00'),
(41, 237, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 13:30:45', '2025-11-09 13:30:45'),
(41, 238, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Policy Owner', NULL, 'System', '2025-11-09 13:30:45', '2025-11-09 13:30:45'),
(41, 239, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Not Active', NULL, 'System', '2025-11-09 13:30:45', '2025-11-09 13:30:45'),
(41, 240, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Diana Wilson', 'Alice Smith', '2025-11-09 13:30:45', '2025-11-09 13:30:45'),
(41, 241, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Policy Owner', 'Alice Smith', '2025-11-09 13:30:45', '2025-11-09 13:30:45'),
(41, 242, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:30:45', '2025-11-09 13:30:45');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `policy_audit_history`
--
ALTER TABLE `policy_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `policy_audit_history`
--
ALTER TABLE `policy_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=243;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `policy_audit_history`
--
ALTER TABLE `policy_audit_history`
  ADD CONSTRAINT `Policy_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `policy` (`ID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
